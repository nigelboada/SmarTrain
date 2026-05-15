from __future__ import annotations

import argparse
import json
import math
import os
from pathlib import Path
from typing import Any

from rag_common import QUESTIONS_PATH, RESULTS_DIR, load_corpus, load_env_file, read_jsonl


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Compare SmarTrain RAG embedding retrievers.")
    parser.add_argument("--chunk-size", type=int, default=700)
    parser.add_argument("--chunk-overlap", type=int, default=90)
    parser.add_argument("--top-k", type=int, default=6)
    parser.add_argument("--ollama-base-url", default=os.environ.get("OLLAMA_BASE_URL", "http://127.0.0.1:11434"))
    parser.add_argument("--out", default=str(RESULTS_DIR / "embedding_model_comparison.json"))
    return parser.parse_args()


def build_chunks(chunk_size: int, chunk_overlap: int):
    from langchain_core.documents import Document
    from langchain_text_splitters import RecursiveCharacterTextSplitter

    docs = [Document(page_content=item["text"], metadata=item["metadata"]) for item in load_corpus()]
    splitter = RecursiveCharacterTextSplitter(chunk_size=chunk_size, chunk_overlap=chunk_overlap)
    chunks = splitter.split_documents(docs)
    for index, chunk in enumerate(chunks):
        chunk.metadata["chunk_id"] = index
        chunk.metadata["id"] = f"{chunk.metadata.get('source', 'unknown')}:{index}"
    return chunks


def cosine(left: list[float], right: list[float]) -> float:
    numerator = sum(a * b for a, b in zip(left, right))
    left_norm = math.sqrt(sum(a * a for a in left))
    right_norm = math.sqrt(sum(b * b for b in right))
    if left_norm == 0 or right_norm == 0:
        return 0.0
    return numerator / (left_norm * right_norm)


def evaluate(name: str, embedder, chunks, questions: list[dict[str, Any]], top_k: int) -> dict[str, Any]:
    chunk_vectors = embedder.embed_documents([chunk.page_content for chunk in chunks])
    rows = []
    hits = 0
    reciprocal_ranks = []
    for question in questions:
        query_vector = embedder.embed_query(question["question"])
        scored = sorted(
            ((cosine(query_vector, vector), chunk) for vector, chunk in zip(chunk_vectors, chunks)),
            key=lambda item: item[0],
            reverse=True,
        )[:top_k]
        retrieved = [chunk.metadata.get("doc_id") or str(chunk.metadata.get("id", "")) for _, chunk in scored]
        expected = question.get("expected_ids", [])
        matching_ranks = [index + 1 for index, item in enumerate(retrieved) if item in expected]
        hits += int(bool(matching_ranks))
        reciprocal_ranks.append(1 / min(matching_ranks) if matching_ranks else 0.0)
        rows.append(
            {
                "question_id": question["id"],
                "question": question["question"],
                "expected_ids": expected,
                "retrieved_ids": retrieved,
                "scores": [round(score, 4) for score, _ in scored],
                "hit": bool(matching_ranks),
            }
        )
    return {
        "embedding_model": name,
        "top_k": top_k,
        "questions": len(questions),
        "hit_rate": round(hits / len(questions), 3) if questions else 0.0,
        "mrr": round(sum(reciprocal_ranks) / len(reciprocal_ranks), 3) if reciprocal_ranks else 0.0,
        "details": rows,
    }


def main() -> None:
    load_env_file()
    args = parse_args()
    questions = read_jsonl(QUESTIONS_PATH)
    chunks = build_chunks(args.chunk_size, args.chunk_overlap)

    from langchain_huggingface import HuggingFaceEmbeddings
    from langchain_ollama import OllamaEmbeddings

    models = [
        (
            "nomic-embed-text",
            OllamaEmbeddings(model="nomic-embed-text", base_url=args.ollama_base_url),
        ),
        (
            "sentence-transformers/all-MiniLM-L6-v2",
            HuggingFaceEmbeddings(
                model_name="sentence-transformers/all-MiniLM-L6-v2",
                model_kwargs={"device": "cpu"},
                encode_kwargs={"normalize_embeddings": True},
            ),
        ),
    ]
    results = [evaluate(name, embedder, chunks, questions, args.top_k) for name, embedder in models]
    winner = sorted(results, key=lambda item: (-item["hit_rate"], -item["mrr"], item["embedding_model"]))[0] if results else None
    output = {
        "chunk_size": args.chunk_size,
        "chunk_overlap": args.chunk_overlap,
        "chunks": len(chunks),
        "results": results,
        "winner": winner,
    }
    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(output, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(output, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
