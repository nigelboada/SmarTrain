from __future__ import annotations

import json
import math
import re
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DATA_PATH = ROOT / "data" / "knowledge_base.jsonl"
QUESTIONS_PATH = ROOT / "eval" / "questions.jsonl"
RESULTS_DIR = ROOT / "results"
RESULTS_PATH = RESULTS_DIR / "rag_evaluation.json"

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


def tokenize(text: str) -> list[str]:
    tokens = re.findall(r"[a-zA-ZÀ-ÿ0-9]+", text.lower())
    return [token for token in tokens if token not in STOPWORDS and len(token) > 1]


def read_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def keyword_overlap_score(query_tokens: list[str], document_tokens: list[str]) -> float:
    if not query_tokens:
        return 0.0
    query_set = set(query_tokens)
    document_set = set(document_tokens)
    return len(query_set & document_set) / len(query_set)


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
    return {
        token: (count / total) * idf.get(token, 0.0)
        for token, count in counts.items()
    }


def cosine_similarity(left: dict[str, float], right: dict[str, float]) -> float:
    shared = set(left) & set(right)
    numerator = sum(left[token] * right[token] for token in shared)
    left_norm = math.sqrt(sum(value * value for value in left.values()))
    right_norm = math.sqrt(sum(value * value for value in right.values()))
    if left_norm == 0 or right_norm == 0:
        return 0.0
    return numerator / (left_norm * right_norm)


def retrieve_keyword(query: str, documents: list[dict], top_k: int) -> list[dict]:
    query_tokens = tokenize(query)
    scored = []
    for document in documents:
        document_tokens = tokenize(document["title"] + " " + document["text"])
        scored.append((keyword_overlap_score(query_tokens, document_tokens), document))
    return [document for _, document in sorted(scored, key=lambda item: item[0], reverse=True)[:top_k]]


def retrieve_tfidf(query: str, documents: list[dict], top_k: int) -> list[dict]:
    document_tokens = [tokenize(document["title"] + " " + document["text"]) for document in documents]
    idf = build_idf(document_tokens)
    document_vectors = [tfidf_vector(tokens, idf) for tokens in document_tokens]
    query_vector = tfidf_vector(tokenize(query), idf)
    scored = [
        (cosine_similarity(query_vector, document_vector), document)
        for document_vector, document in zip(document_vectors, documents)
    ]
    return [document for _, document in sorted(scored, key=lambda item: item[0], reverse=True)[:top_k]]


def evaluate(name: str, retriever, documents: list[dict], questions: list[dict], top_k: int) -> dict:
    hits = 0
    reciprocal_ranks = []
    detailed = []

    for question in questions:
        retrieved = retriever(question["question"], documents, top_k)
        retrieved_ids = [document["id"] for document in retrieved]
        expected_ids = question["expected_ids"]
        matching_ranks = [
            index + 1
            for index, retrieved_id in enumerate(retrieved_ids)
            if retrieved_id in expected_ids
        ]
        hit = bool(matching_ranks)
        hits += int(hit)
        reciprocal_ranks.append(1 / min(matching_ranks) if matching_ranks else 0.0)
        detailed.append(
            {
                "question_id": question["id"],
                "question": question["question"],
                "expected_ids": expected_ids,
                "retrieved_ids": retrieved_ids,
                "hit": hit,
            }
        )

    return {
        "retriever": name,
        "top_k": top_k,
        "hit_rate": hits / len(questions),
        "mrr": sum(reciprocal_ranks) / len(reciprocal_ranks),
        "details": detailed,
    }


def build_answer(question: str, retrieved_documents: list[dict]) -> str:
    context = " ".join(document["text"] for document in retrieved_documents[:2])
    return f"Pregunta: {question}\nResposta basada en context: {context}"


def main() -> None:
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    documents = read_jsonl(DATA_PATH)
    questions = read_jsonl(QUESTIONS_PATH)

    evaluations = [
        evaluate("keyword_overlap", retrieve_keyword, documents, questions, top_k=3),
        evaluate("tfidf_cosine", retrieve_tfidf, documents, questions, top_k=3),
    ]

    examples = []
    for question in questions[:3]:
        retrieved = retrieve_tfidf(question["question"], documents, top_k=3)
        examples.append(
            {
                "question": question["question"],
                "retrieved_ids": [document["id"] for document in retrieved],
                "answer": build_answer(question["question"], retrieved),
            }
        )

    output = {
        "corpus_documents": len(documents),
        "questions": len(questions),
        "evaluations": evaluations,
        "selected_retriever": "tfidf_cosine",
        "examples": examples,
    }
    RESULTS_PATH.write_text(json.dumps(output, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(output, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
