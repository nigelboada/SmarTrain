from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any


RAG_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = RAG_ROOT.parents[1]
DATA_PATH = RAG_ROOT / "data" / "knowledge_base.jsonl"
RESULTS_DIR = RAG_ROOT / "results"
CHROMA_DIR = RAG_ROOT / "chroma_db" / "smartrain"
COLLECTION_NAME = "smartrain_rag"


@dataclass(frozen=True)
class RagSource:
    path: Path
    source: str
    category: str
    language: str = "ca"


DEFAULT_SOURCES = [
    RagSource(DATA_PATH, "knowledge_base.jsonl", "knowledge_base", "ca"),
    RagSource(PROJECT_ROOT / "docs" / "PROJECT_REPORT.md", "PROJECT_REPORT.md", "project_report", "ca"),
    RagSource(RAG_ROOT / "results" / "MODEL_COMPARISON.md", "MODEL_COMPARISON.md", "model_selection", "ca"),
    RagSource(RAG_ROOT / "results" / "generation_model_comparison_report.md", "generation_model_comparison_report.md", "rag_evaluation", "ca"),
    RagSource(RAG_ROOT / "results" / "model_selection_report.md", "model_selection_report.md", "model_selection", "ca"),
    RagSource(RAG_ROOT / "eval" / "sessions.jsonl", "sessions.jsonl", "anonymous_sessions", "ca"),
]


def load_env_file(path: Path | None = None) -> None:
    env_path = path or PROJECT_ROOT / ".env"
    if not env_path.exists():
        return
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def knowledge_base_as_text(path: Path) -> list[dict[str, Any]]:
    rows = []
    for item in read_jsonl(path):
        rows.append(
            {
                "text": f"{item.get('title', '')}\n{item.get('category', '')}\n{item.get('text', '')}",
                "metadata": {
                    "source": path.name,
                    "category": item.get("category", "knowledge_base"),
                    "language": item.get("language", "ca"),
                    "doc_id": item.get("id", ""),
                },
            }
        )
    return rows


def session_eval_as_text(path: Path) -> list[dict[str, Any]]:
    rows = []
    for item in read_jsonl(path):
        session = item.get("session", {})
        if not session:
            continue
        text = (
            f"Sessio anonimitzada {item.get('id', '')}. "
            f"Activitat dominant: {session.get('dominantActivity', '')}. "
            f"Confianca mitjana: {int(float(session.get('avgMlConfidence', 0)) * 100)}%. "
            f"Prediccions ML: {session.get('mlPredictionCount', 0)}. "
            f"Blocs alta intensitat: {session.get('highIntensityCount', 0)}. "
            f"Notes esperades: {item.get('notes', '')}. "
            f"Termes esperats: {', '.join(item.get('expected_terms', []))}."
        )
        rows.append(
            {
                "text": text,
                "metadata": {
                    "source": path.name,
                    "category": "anonymous_sessions",
                    "language": "ca",
                    "doc_id": item.get("id", ""),
                },
            }
        )
    return rows


def plain_file_as_text(source: RagSource) -> list[dict[str, Any]]:
    if not source.path.exists():
        return []
    return [
        {
            "text": source.path.read_text(encoding="utf-8", errors="replace"),
            "metadata": {
                "source": source.source,
                "category": source.category,
                "language": source.language,
                "doc_id": source.path.stem,
            },
        }
    ]


def load_corpus(sources: list[RagSource] | None = None) -> list[dict[str, Any]]:
    corpus = []
    for source in sources or DEFAULT_SOURCES:
        if not source.path.exists():
            continue
        if source.path.name == "knowledge_base.jsonl":
            corpus.extend(knowledge_base_as_text(source.path))
        elif source.path.name == "sessions.jsonl":
            corpus.extend(session_eval_as_text(source.path))
        else:
            corpus.extend(plain_file_as_text(source))
    return corpus


def require_package(import_name: str, install_hint: str) -> Any:
    try:
        module = __import__(import_name)
    except ImportError as exc:
        raise SystemExit(f"Missing dependency '{import_name}'. Install with: {install_hint}") from exc
    return module
