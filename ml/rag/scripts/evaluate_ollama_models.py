from __future__ import annotations

import argparse
import csv
import json
import math
import re
import time
from collections import Counter
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
DATA_PATH = ROOT / "data" / "knowledge_base.jsonl"
QUESTIONS_PATH = ROOT / "eval" / "questions.jsonl"
SESSIONS_PATH = ROOT / "eval" / "sessions.jsonl"
RESULTS_DIR = ROOT / "results"
JSON_RESULTS_PATH = RESULTS_DIR / "ollama_model_comparison.json"
CSV_RESULTS_PATH = RESULTS_DIR / "ollama_model_comparison.csv"

STOPWORDS = {
    "a",
    "al",
    "amb",
    "de",
    "del",
    "el",
    "els",
    "en",
    "es",
    "i",
    "la",
    "les",
    "per",
    "que",
    "quin",
    "quina",
    "quines",
    "quins",
    "s",
    "si",
    "te",
    "un",
    "una",
}


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def tokenize(text: str) -> list[str]:
    tokens = re.findall(r"[a-zA-ZÀ-ÿ0-9]+", text.lower())
    return [token for token in tokens if token not in STOPWORDS and len(token) > 1]


def build_idf(document_tokens: list[list[str]]) -> dict[str, float]:
    document_count = len(document_tokens)
    df = Counter()
    for tokens in document_tokens:
        df.update(set(tokens))
    return {
        token: math.log((document_count + 1) / (frequency + 1)) + 1
        for token, frequency in df.items()
    }


def tfidf_vector(tokens: list[str], idf: dict[str, float]) -> dict[str, float]:
    counts = Counter(tokens)
    total = sum(counts.values()) or 1
    return {token: (count / total) * idf.get(token, 0.0) for token, count in counts.items()}


def cosine_similarity(left: dict[str, float], right: dict[str, float]) -> float:
    shared = set(left) & set(right)
    numerator = sum(left[token] * right[token] for token in shared)
    left_norm = math.sqrt(sum(value * value for value in left.values()))
    right_norm = math.sqrt(sum(value * value for value in right.values()))
    if left_norm == 0 or right_norm == 0:
        return 0.0
    return numerator / (left_norm * right_norm)


class TfidfRetriever:
    def __init__(self, documents: list[dict[str, Any]]) -> None:
        self.documents = documents
        self.document_tokens = [
            tokenize(f"{document['title']} {document['category']} {document['text']}")
            for document in documents
        ]
        self.idf = build_idf(self.document_tokens)
        self.document_vectors = [tfidf_vector(tokens, self.idf) for tokens in self.document_tokens]

    def retrieve(self, query: str, top_k: int) -> list[dict[str, Any]]:
        query_vector = tfidf_vector(tokenize(query), self.idf)
        scored = [
            (cosine_similarity(query_vector, document_vector), document)
            for document_vector, document in zip(self.document_vectors, self.documents)
        ]
        return [document for _, document in sorted(scored, key=lambda item: item[0], reverse=True)[:top_k]]


def session_to_query(session: dict[str, Any]) -> str:
    confidence_percent = int(float(session["avgMlConfidence"]) * 100)
    return (
        f"Activitat dominant: {session['dominantActivity']}. "
        f"Confianca mitjana: {confidence_percent}%. "
        f"Blocs alta intensitat: {session['highIntensityCount']}/{session['mlPredictionCount']}."
    )


def build_prompt(task: dict[str, Any], retrieved: list[dict[str, Any]]) -> str:
    context = "\n".join(
        f"[{document['id']}] {document['title']} ({document['category']}): {document['text']}"
        for document in retrieved
    )
    if task["type"] == "question":
        user_input = f"Pregunta: {task['question']}"
    else:
        user_input = f"Resum de sessio: {session_to_query(task['session'])}"

    return (
        "Ets l'assistent de SmarTrain. Respon en catala, de forma breu i prudent.\n"
        "Basa la resposta nomes en el context recuperat i en les dades de la sessio.\n"
        "No inventis metriques ni diagnositcs medics. Inclou una recomanacio accionable.\n\n"
        f"Context RAG:\n{context}\n\n"
        f"{user_input}\n\n"
        "Resposta:"
    )


def call_ollama(base_url: str, model: str, prompt: str, timeout_seconds: int) -> tuple[str, dict[str, Any]]:
    payload = {
        "model": model,
        "prompt": prompt,
        "stream": False,
        "options": {
            "temperature": 0.2,
            "num_predict": 220,
        },
    }
    request = Request(
        f"{base_url.rstrip('/')}/api/generate",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    started = time.perf_counter()
    with urlopen(request, timeout=timeout_seconds) as response:
        raw = response.read().decode("utf-8")
    latency_ms = round((time.perf_counter() - started) * 1000, 2)
    data = json.loads(raw)
    metadata = {
        "latency_ms": latency_ms,
        "total_duration_ns": data.get("total_duration"),
        "eval_count": data.get("eval_count"),
        "eval_duration_ns": data.get("eval_duration"),
        "prompt_eval_count": data.get("prompt_eval_count"),
    }
    return data.get("response", "").strip(), metadata


def source_hit_rate(expected_ids: list[str], retrieved_ids: list[str]) -> float | None:
    if not expected_ids:
        return None
    return len(set(expected_ids) & set(retrieved_ids)) / len(set(expected_ids))


def expected_term_coverage(expected_terms: list[str], answer: str) -> float | None:
    if not expected_terms:
        return None
    normalized_answer = answer.lower()
    hits = sum(1 for term in expected_terms if term.lower() in normalized_answer)
    return hits / len(expected_terms)


def grounded_overlap(answer: str, retrieved: list[dict[str, Any]]) -> float:
    answer_tokens = set(tokenize(answer))
    if not answer_tokens:
        return 0.0
    context_tokens = set(tokenize(" ".join(document["text"] for document in retrieved)))
    return len(answer_tokens & context_tokens) / len(answer_tokens)


def build_tasks(questions: list[dict[str, Any]], sessions: list[dict[str, Any]]) -> list[dict[str, Any]]:
    tasks = []
    for question in questions:
        tasks.append(
            {
                "type": "question",
                "id": question["id"],
                "question": question["question"],
                "query": question["question"],
                "expected_ids": question.get("expected_ids", []),
                "expected_terms": [],
            }
        )
    for item in sessions:
        query = session_to_query(item["session"])
        tasks.append(
            {
                "type": "session",
                "id": item["id"],
                "session": item["session"],
                "query": query,
                "expected_ids": [],
                "expected_terms": item.get("expected_terms", []),
                "notes": item.get("notes", ""),
            }
        )
    return tasks


def write_csv(rows: list[dict[str, Any]], path: Path) -> None:
    fieldnames = [
        "model",
        "task_id",
        "task_type",
        "latency_ms",
        "source_hit_rate",
        "expected_term_coverage",
        "grounded_overlap",
        "answer_chars",
        "error",
        "retrieved_ids",
    ]
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field) for field in fieldnames})


def main() -> None:
    parser = argparse.ArgumentParser(description="Compare Ollama models on the SmarTrain RAG task.")
    parser.add_argument("--models", nargs="+", default=["gemma3:1b"], help="Ollama model names to evaluate.")
    parser.add_argument("--base-url", default="http://127.0.0.1:11434", help="Ollama API base URL.")
    parser.add_argument("--top-k", type=int, default=3, help="Number of RAG documents to retrieve.")
    parser.add_argument("--timeout", type=int, default=120, help="Request timeout per generation.")
    parser.add_argument("--limit", type=int, default=0, help="Optional maximum number of tasks.")
    args = parser.parse_args()

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    documents = read_jsonl(DATA_PATH)
    questions = read_jsonl(QUESTIONS_PATH)
    sessions = read_jsonl(SESSIONS_PATH)
    retriever = TfidfRetriever(documents)
    tasks = build_tasks(questions, sessions)
    if args.limit > 0:
        tasks = tasks[: args.limit]

    rows: list[dict[str, Any]] = []
    detailed: list[dict[str, Any]] = []

    for model in args.models:
        for task in tasks:
            retrieved = retriever.retrieve(task["query"], top_k=args.top_k)
            retrieved_ids = [document["id"] for document in retrieved]
            prompt = build_prompt(task, retrieved)
            error = ""
            answer = ""
            metadata: dict[str, Any] = {"latency_ms": None}
            try:
                answer, metadata = call_ollama(args.base_url, model, prompt, args.timeout)
            except (HTTPError, URLError, TimeoutError, OSError) as exc:
                error = str(exc)

            row = {
                "model": model,
                "task_id": task["id"],
                "task_type": task["type"],
                "prompt": prompt,
                "answer": answer,
                "answer_chars": len(answer),
                "retrieved_ids": "|".join(retrieved_ids),
                "retrieved_titles": [document["title"] for document in retrieved],
                "source_hit_rate": source_hit_rate(task.get("expected_ids", []), retrieved_ids),
                "expected_term_coverage": expected_term_coverage(task.get("expected_terms", []), answer),
                "grounded_overlap": grounded_overlap(answer, retrieved) if answer else 0.0,
                "error": error,
                **metadata,
            }
            rows.append(row)
            detailed.append({**row, "task": task})
            print(
                json.dumps(
                    {
                        "model": model,
                        "task_id": task["id"],
                        "latency_ms": row["latency_ms"],
                        "error": error,
                    },
                    ensure_ascii=False,
                )
            )

    summary = []
    for model in args.models:
        model_rows = [row for row in rows if row["model"] == model]
        successful = [row for row in model_rows if not row["error"]]
        latencies = [row["latency_ms"] for row in successful if row["latency_ms"] is not None]
        summary.append(
            {
                "model": model,
                "tasks": len(model_rows),
                "successful_tasks": len(successful),
                "avg_latency_ms": round(sum(latencies) / len(latencies), 2) if latencies else None,
                "avg_grounded_overlap": round(
                    sum(row["grounded_overlap"] for row in successful) / len(successful), 3
                )
                if successful
                else None,
                "avg_expected_term_coverage": round(
                    sum(
                        row["expected_term_coverage"]
                        for row in successful
                        if row["expected_term_coverage"] is not None
                    )
                    / max(1, len([row for row in successful if row["expected_term_coverage"] is not None])),
                    3,
                )
                if successful
                else None,
                "errors": len([row for row in model_rows if row["error"]]),
            }
        )

    output = {
        "base_url": args.base_url,
        "top_k": args.top_k,
        "documents": len(documents),
        "questions": len(questions),
        "sessions": len(sessions),
        "models": args.models,
        "summary": summary,
        "details": detailed,
    }
    JSON_RESULTS_PATH.write_text(json.dumps(output, indent=2, ensure_ascii=False), encoding="utf-8")
    write_csv(rows, CSV_RESULTS_PATH)
    print(json.dumps({"summary": summary, "json": str(JSON_RESULTS_PATH), "csv": str(CSV_RESULTS_PATH)}, indent=2))


if __name__ == "__main__":
    main()
