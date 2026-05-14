from __future__ import annotations

import argparse
import csv
import json
import os
import statistics
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

sys.path.append(str(Path(__file__).resolve().parent))

from evaluate_ollama_models import (  # noqa: E402
    DATA_PATH,
    QUESTIONS_PATH,
    RESULTS_DIR,
    SESSIONS_PATH,
    TfidfRetriever,
    build_prompt,
    build_tasks,
    call_ollama,
    expected_term_coverage,
    grounded_overlap,
    read_jsonl,
    recommendation_hint_score,
    session_to_query,
    source_hit_rate,
    tokenize,
)


DEFAULT_LOCAL_MODELS = ["gemma3:1b", "gemma4:latest", "qwen3.6:latest"]
DEFAULT_CLOUD_MODELS = ["qwen3-coder-next", "gemma3:4b", "gpt-oss:20b"]

COMPARISON_JSON_PATH = RESULTS_DIR / "generation_model_comparison.json"
COMPARISON_CSV_PATH = RESULTS_DIR / "generation_model_comparison.csv"
REPORT_PATH = RESULTS_DIR / "generation_model_comparison_report.md"

CATALAN_MARKERS = {
    "sessio",
    "confianca",
    "recoman",
    "recuperacio",
    "carrega",
    "activitat",
    "prediccio",
    "prudencia",
    "intensitat",
    "model",
    "dades",
    "usuari",
}

SPANISH_OR_ENGLISH_MARKERS = {
    "session",
    "confidence",
    "recommendation",
    "activity",
    "model says",
    "entrenamiento",
    "recuperacion",
    "datos",
}

MEDICAL_RISK_MARKERS = {
    "diagnostic",
    "diagnostica",
    "lesio",
    "lesion",
    "malaltia",
    "enfermedad",
    "tractament",
    "tratamiento",
}

UNSUPPORTED_NUMERIC_MARKERS = {
    "100%",
    "garant",
    "segur que",
    "sempre",
    "mai",
    "exactament",
}


def linguistic_quality_score(answer: str) -> float:
    if not answer.strip():
        return 0.0
    normalized = " ".join(tokenize(answer))
    tokens = normalized.split()
    if not tokens:
        return 0.0
    marker_hits = sum(1 for marker in CATALAN_MARKERS if marker in normalized)
    foreign_hits = sum(1 for marker in SPANISH_OR_ENGLISH_MARKERS if marker in normalized)
    length_score = 1.0 if 35 <= len(tokens) <= 140 else 0.65 if 15 <= len(tokens) <= 180 else 0.35
    catalan_score = min(1.0, marker_hits / 5)
    penalty = min(0.4, foreign_hits * 0.12)
    return clamp((0.65 * catalan_score) + (0.35 * length_score) - penalty)


def context_respect_score(answer: str, retrieved: list[dict[str, Any]], source_hit: float | None) -> float:
    if not answer.strip():
        return 0.0
    overlap = grounded_overlap(answer, retrieved)
    source_component = source_hit if source_hit is not None else 1.0
    return clamp((0.75 * overlap) + (0.25 * source_component))


def invented_data_risk_score(answer: str, retrieved: list[dict[str, Any]], task: dict[str, Any]) -> float:
    if not answer.strip():
        return 1.0
    normalized = answer.lower()
    context_text = " ".join(document["text"] for document in retrieved).lower()
    allowed_numbers = set()
    for text in [context_text, task.get("query", ""), json.dumps(task.get("session", {}))]:
        allowed_numbers.update(token for token in tokenize(text) if any(char.isdigit() for char in token))
    answer_numbers = {token for token in tokenize(answer) if any(char.isdigit() for char in token)}
    unsupported_numbers = answer_numbers - allowed_numbers
    marker_hits = sum(1 for marker in MEDICAL_RISK_MARKERS | UNSUPPORTED_NUMERIC_MARKERS if marker in normalized)
    risk = 0.0
    risk += min(0.45, len(unsupported_numbers) * 0.15)
    risk += min(0.45, marker_hits * 0.15)
    risk += 0.2 if grounded_overlap(answer, retrieved) < 0.25 else 0.0
    return clamp(risk)


def recommendation_usefulness_score(answer: str, task: dict[str, Any]) -> float:
    if not answer.strip():
        return 0.0
    normalized = answer.lower()
    action_markers = ["recoman", "prioritza", "alterna", "revisa", "inclou", "cal ", "pots ", "conve"]
    caution_markers = ["prud", "confianca baixa", "orientatiu", "limitacio", "no substitueix"]
    expected_terms = task.get("expected_terms", [])
    action_score = 1.0 if any(marker in normalized for marker in action_markers) else 0.0
    caution_score = 0.4 if any(marker in normalized for marker in caution_markers) else 0.0
    term_score = expected_term_coverage(expected_terms, answer)
    if term_score is None:
        term_score = 0.6
    return clamp((0.45 * action_score) + (0.35 * term_score) + (0.2 * min(1.0, caution_score + 0.6)))


def clamp(value: float) -> float:
    return max(0.0, min(1.0, value))


def repair_mojibake(text: str) -> str:
    if "Ã" not in text and "â" not in text:
        return text
    try:
        return text.encode("cp1252").decode("utf-8")
    except UnicodeError:
        return text


def to_1_5(score: float, inverse: bool = False) -> float:
    value = 1.0 - score if inverse else score
    return round(1 + (4 * clamp(value)), 2)


def evaluate_model(
    provider: str,
    base_url: str,
    model: str,
    api_key: str,
    tasks: list[dict[str, Any]],
    retriever: TfidfRetriever,
    top_k: int,
    timeout: int,
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for task in tasks:
        retrieved = retriever.retrieve(task["query"], top_k=top_k)
        retrieved_ids = [document["id"] for document in retrieved]
        prompt = build_prompt(task, retrieved)
        answer = ""
        error = ""
        metadata: dict[str, Any] = {"latency_ms": None}
        try:
            answer, metadata = call_ollama(base_url, model, prompt, timeout, api_key)
        except Exception as exc:  # noqa: BLE001 - experiment runner records failures as data.
            error = str(exc)
        answer = repair_mojibake(answer)
        if not error and not answer.strip():
            error = "empty response"

        source_hit = source_hit_rate(task.get("expected_ids", []), retrieved_ids)
        grounded = grounded_overlap(answer, retrieved) if answer else 0.0
        term_coverage = expected_term_coverage(task.get("expected_terms", []), answer)
        linguistic = linguistic_quality_score(answer)
        context_score = context_respect_score(answer, retrieved, source_hit)
        invented_risk = invented_data_risk_score(answer, retrieved, task)
        usefulness = recommendation_usefulness_score(answer, task)
        recommendation_hint = recommendation_hint_score(answer)

        row = {
            "provider": provider,
            "base_url": base_url,
            "model": model,
            "task_id": task["id"],
            "task_type": task["type"],
            "latency_ms": metadata.get("latency_ms"),
            "error": error,
            "answer": answer,
            "answer_chars": len(answer),
            "retrieved_ids": "|".join(retrieved_ids),
            "source_hit_rate": source_hit,
            "expected_term_coverage": term_coverage,
            "grounded_overlap": grounded,
            "recommendation_hint_score": recommendation_hint,
            "linguistic_quality_score": linguistic,
            "context_respect_score": context_score,
            "invented_data_risk_score": invented_risk,
            "recommendation_usefulness_score": usefulness,
            "linguistic_quality_1_5": to_1_5(linguistic),
            "context_respect_1_5": to_1_5(context_score),
            "invented_data_1_5": to_1_5(invented_risk, inverse=True),
            "recommendation_usefulness_1_5": to_1_5(usefulness),
            "prompt": prompt,
            "task": task,
        }
        rows.append(row)
        print(
            json.dumps(
                {
                    "provider": provider,
                    "model": model,
                    "task_id": task["id"],
                    "latency_ms": row["latency_ms"],
                    "error": error,
                },
                ensure_ascii=False,
            )
        )
    return rows


def average(values: list[float | int | None]) -> float | None:
    numeric = [float(value) for value in values if value is not None]
    return round(sum(numeric) / len(numeric), 3) if numeric else None


def summarize(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    summary = []
    keys = sorted({(row["provider"], row["model"]) for row in rows})
    for provider, model in keys:
        model_rows = [row for row in rows if row["provider"] == provider and row["model"] == model]
        successful = [row for row in model_rows if not row["error"] and row["answer_chars"] > 0]
        latencies = [row["latency_ms"] for row in successful if row["latency_ms"] is not None]
        summary.append(
            {
                "provider": provider,
                "model": model,
                "tasks": len(model_rows),
                "successful_tasks": len(successful),
                "errors_or_timeouts": len(model_rows) - len(successful),
                "avg_latency_ms": round(statistics.mean(latencies), 2) if latencies else None,
                "p95_latency_ms": round(statistics.quantiles(latencies, n=20)[18], 2) if len(latencies) >= 2 else (round(latencies[0], 2) if latencies else None),
                "avg_grounded_overlap": average([row["grounded_overlap"] for row in successful]),
                "avg_expected_term_coverage": average([row["expected_term_coverage"] for row in successful]),
                "avg_linguistic_quality_1_5": average([row["linguistic_quality_1_5"] for row in successful]),
                "avg_context_respect_1_5": average([row["context_respect_1_5"] for row in successful]),
                "avg_invented_data_1_5": average([row["invented_data_1_5"] for row in successful]),
                "avg_recommendation_usefulness_1_5": average([row["recommendation_usefulness_1_5"] for row in successful]),
                "avg_recommendation_hint_score": average([row["recommendation_hint_score"] for row in successful]),
            }
        )
    return sorted(
        summary,
        key=lambda item: (
            item["errors_or_timeouts"],
            -(item["avg_context_respect_1_5"] or 0),
            -(item["avg_recommendation_usefulness_1_5"] or 0),
            item["avg_latency_ms"] or 999999,
        ),
    )


def choose_winner(summary: list[dict[str, Any]]) -> dict[str, Any] | None:
    viable = [
        item
        for item in summary
        if item["successful_tasks"] >= max(1, int(item["tasks"] * 0.8))
        and (item["avg_context_respect_1_5"] or 0) >= 3.5
        and (item["avg_invented_data_1_5"] or 0) >= 4.0
        and (item["avg_latency_ms"] or 999999) <= 10000
    ]
    if not viable:
        return summary[0] if summary else None
    return sorted(
        viable,
        key=lambda item: (
            -(item["avg_recommendation_usefulness_1_5"] or 0),
            -(item["avg_linguistic_quality_1_5"] or 0),
            -(item["avg_context_respect_1_5"] or 0),
            item["avg_latency_ms"] or 999999,
        ),
    )[0]


def sample_answer(rows: list[dict[str, Any]], provider: str, model: str) -> dict[str, Any] | None:
    preferred = [
        row
        for row in rows
        if row["provider"] == provider and row["model"] == model and row["task_id"] in {"q_001", "s_004"} and row["answer"].strip()
    ]
    if preferred:
        return preferred[0]
    fallback = [row for row in rows if row["provider"] == provider and row["model"] == model and row["answer"].strip()]
    return fallback[0] if fallback else None


def write_csv(rows: list[dict[str, Any]], path: Path) -> None:
    fields = [
        "provider",
        "model",
        "task_id",
        "task_type",
        "latency_ms",
        "error",
        "retrieved_ids",
        "source_hit_rate",
        "expected_term_coverage",
        "grounded_overlap",
        "linguistic_quality_1_5",
        "context_respect_1_5",
        "invented_data_1_5",
        "recommendation_usefulness_1_5",
        "answer_chars",
        "answer",
    ]
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in fields})


def write_report(summary: list[dict[str, Any]], rows: list[dict[str, Any]], path: Path, started_at: str, winner: dict[str, Any] | None) -> None:
    lines = [
        "# Comparativa de models generatius per SmarTrain",
        "",
        f"Data: {started_at}",
        "",
        "## Metodologia",
        "",
        "Tots els models s'han avaluat amb les mateixes 10 tasques del RAG: 6 preguntes documentals i 4 sessions sintetiques. El retrieval es mante constant amb TF-IDF i `top_k=3`; per tant, la comparacio mesura principalment la generacio.",
        "",
        "Les metriques qualitatives son automatices i orientatives: combinen solapament amb el context recuperat, cobertura de termes esperats, marcadors de catala, deteccio de xifres no suportades i presencia de recomanacions accionables. No substitueixen una auditoria humana, pero eviten haver d'omplir un CSV manual per al prototip.",
        "",
        "## Resultats resum",
        "",
        "| Proveidor | Model | Tasques OK | Errors/timeouts | Latencia mitjana | P95 latencia | Catala | Respecte RAG | No invencio | Utilitat | Grounded overlap |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for item in summary:
        lines.append(
            "| {provider} | `{model}` | {successful_tasks}/{tasks} | {errors_or_timeouts} | {avg_latency_ms} ms | {p95_latency_ms} ms | {avg_linguistic_quality_1_5} | {avg_context_respect_1_5} | {avg_invented_data_1_5} | {avg_recommendation_usefulness_1_5} | {avg_grounded_overlap} |".format(
                **{key: value if value is not None else "n/a" for key, value in item.items()}
            )
        )
    lines.extend(["", "## Exemples de resposta", ""])
    for item in summary:
        example = sample_answer(rows, item["provider"], item["model"])
        lines.append(f"### {item['provider']} - `{item['model']}`")
        if not example:
            lines.append("")
            lines.append("No hi ha resposta valida; el model ha fallat o ha retornat buit.")
            lines.append("")
            continue
        answer = example["answer"].replace("\n", " ").strip()
        if len(answer) > 650:
            answer = answer[:647].rstrip() + "..."
        lines.append("")
        lines.append(f"Tasca: `{example['task_id']}`")
        lines.append("")
        lines.append(f"> {answer}")
        lines.append("")
    lines.extend(["## Decisio final", ""])
    if winner:
        lines.append(f"Model recomanat: **`{winner['model']}` ({winner['provider']})**.")
        lines.append("")
        lines.append(
        "La decisio es basa en l'equilibri entre 10/10 tasques correctes, latencia, respecte del context RAG, baixa invencio de dades i utilitat de la recomanacio."
        )
        if winner["provider"] == "cloud":
            lines.append(
                "Com que es cloud, s'ha de mantenir el fallback local per garantir que l'app continua funcionant sense connexio o si la API falla."
            )
        lines.append(
            "Els models amb latencia mitjana superior a 10 segons no es consideren recomanables com a opcio principal de l'app, encara que puguin tenir bones puntuacions qualitatives."
        )
    else:
        lines.append("No s'ha pogut seleccionar cap model per falta de resultats valids.")
    lines.extend(
        [
            "",
            "## Fitxers generats",
            "",
            f"- `{COMPARISON_JSON_PATH.as_posix()}`",
            f"- `{COMPARISON_CSV_PATH.as_posix()}`",
            f"- `{REPORT_PATH.as_posix()}`",
            "",
        ]
    )
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="Compare local and cloud Ollama-compatible models for SmarTrain RAG.")
    parser.add_argument("--local-models", nargs="*", default=DEFAULT_LOCAL_MODELS)
    parser.add_argument("--cloud-models", nargs="*", default=DEFAULT_CLOUD_MODELS)
    parser.add_argument("--local-base-url", default="http://127.0.0.1:11434")
    parser.add_argument("--cloud-base-url", default="https://ollama.com")
    parser.add_argument("--api-key-env", default="OLLAMA_API_KEY")
    parser.add_argument("--top-k", type=int, default=3)
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--from-json", default="", help="Regenerate CSV/report from an existing comparison JSON without rerunning models.")
    args = parser.parse_args()

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    if args.from_json:
        existing = json.loads(Path(args.from_json).read_text(encoding="utf-8"))
        rows = existing["details"]
        for row in rows:
            row["answer"] = repair_mojibake(row.get("answer", ""))
        summary = summarize(rows)
        winner = choose_winner(summary)
        existing["summary"] = summary
        existing["winner"] = winner
        COMPARISON_JSON_PATH.write_text(json.dumps(existing, indent=2, ensure_ascii=False), encoding="utf-8")
        write_csv(rows, COMPARISON_CSV_PATH)
        write_report(summary, rows, REPORT_PATH, existing.get("started_at", "unknown"), winner)
        print(json.dumps({"summary": summary, "winner": winner, "report": str(REPORT_PATH)}, indent=2, ensure_ascii=False))
        return

    documents = read_jsonl(DATA_PATH)
    questions = read_jsonl(QUESTIONS_PATH)
    sessions = read_jsonl(SESSIONS_PATH)
    tasks = build_tasks(questions, sessions)
    if args.limit > 0:
        tasks = tasks[: args.limit]

    retriever = TfidfRetriever(documents)
    rows: list[dict[str, Any]] = []
    api_key = os.environ.get(args.api_key_env, "")
    started_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    for model in args.local_models:
        rows.extend(evaluate_model("local", args.local_base_url, model, "", tasks, retriever, args.top_k, args.timeout))
    for model in args.cloud_models:
        rows.extend(evaluate_model("cloud", args.cloud_base_url, model, api_key, tasks, retriever, args.top_k, args.timeout))

    summary = summarize(rows)
    winner = choose_winner(summary)
    output = {
        "started_at": started_at,
        "documents": len(documents),
        "tasks": len(tasks),
        "local_models": args.local_models,
        "cloud_models": args.cloud_models,
        "summary": summary,
        "winner": winner,
        "details": rows,
    }
    COMPARISON_JSON_PATH.write_text(json.dumps(output, indent=2, ensure_ascii=False), encoding="utf-8")
    write_csv(rows, COMPARISON_CSV_PATH)
    write_report(summary, rows, REPORT_PATH, started_at, winner)
    print(json.dumps({"summary": summary, "winner": winner, "report": str(REPORT_PATH)}, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
