from __future__ import annotations

import os
import time
from pathlib import Path
from typing import Any

import requests
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from rag_common import CHROMA_DIR, COLLECTION_NAME, load_env_file


load_env_file()

APP_TITLE = "SmarTrain RAG Backend"
DEFAULT_PROMPT = """Ets l'assistent de SmarTrain.
Respon només amb el context recuperat i les dades de la sessió.
Si no hi ha informació suficient al context, digues-ho clarament.
No inventis mètriques ni diagnòstics mèdics.
Inclou una recomanació accionable i prudent.
No escriguis raonament intern, només la resposta final."""


class SessionPayload(BaseModel):
    dominantActivity: str = ""
    avgMlConfidence: float = 0.0
    mlPredictionCount: int = 0
    highIntensityCount: int = 0
    durationSeconds: int = 0
    distanceMetres: float = 0.0


class RagRequest(BaseModel):
    session: SessionPayload
    language: str = "ca"
    top_k: int = Field(default_factory=lambda: int(os.environ.get("SMARTRAIN_RAG_TOP_K", "6")))


class SourceChunk(BaseModel):
    id: str
    source: str
    category: str
    language: str
    chunk_id: int
    score: float
    text: str


class RagResponse(BaseModel):
    title: str
    answer: str
    provider: str
    model: str
    latency_ms: int
    sources: list[SourceChunk]


app = FastAPI(title=APP_TITLE)
_vectorstore = None


def get_vectorstore():
    global _vectorstore
    if _vectorstore is not None:
        return _vectorstore

    from langchain_chroma import Chroma
    from langchain_ollama import OllamaEmbeddings

    chroma_dir = os.environ.get("SMARTRAIN_RAG_CHROMA_DIR", str(CHROMA_DIR))
    collection = os.environ.get("SMARTRAIN_RAG_COLLECTION", COLLECTION_NAME)
    embed_model = os.environ.get("SMARTRAIN_RAG_EMBED_MODEL", "nomic-embed-text")
    ollama_base_url = os.environ.get("OLLAMA_BASE_URL", "http://127.0.0.1:11434")
    if not Path(chroma_dir).exists():
        raise HTTPException(status_code=503, detail=f"Chroma index not found at {chroma_dir}. Run build_vector_index.py first.")

    embeddings = OllamaEmbeddings(model=embed_model, base_url=ollama_base_url)
    _vectorstore = Chroma(
        persist_directory=chroma_dir,
        collection_name=collection,
        embedding_function=embeddings,
    )
    return _vectorstore


def session_to_query(session: SessionPayload, language: str) -> str:
    confidence = int(session.avgMlConfidence * 100)
    if language == "en":
        return (
            f"Dominant activity: {session.dominantActivity}. "
            f"Average confidence: {confidence}%. "
            f"High-intensity blocks: {session.highIntensityCount}/{session.mlPredictionCount}. "
            f"Duration: {session.durationSeconds}s. Distance: {session.distanceMetres:.1f}m."
        )
    if language == "es":
        return (
            f"Actividad dominante: {session.dominantActivity}. "
            f"Confianza media: {confidence}%. "
            f"Bloques de alta intensidad: {session.highIntensityCount}/{session.mlPredictionCount}. "
            f"Duracion: {session.durationSeconds}s. Distancia: {session.distanceMetres:.1f}m."
        )
    return (
        f"Activitat dominant: {session.dominantActivity}. "
        f"Confianca mitjana: {confidence}%. "
        f"Blocs alta intensitat: {session.highIntensityCount}/{session.mlPredictionCount}. "
        f"Durada: {session.durationSeconds}s. Distancia: {session.distanceMetres:.1f}m."
    )


def language_instruction(language: str) -> str:
    return {
        "en": "Answer in English.",
        "es": "Responde en castellano.",
        "zh": "请用中文回答。",
        "ca": "Respon en catala.",
    }.get(language, "Respon en catala.")


def build_prompt(request: RagRequest, sources: list[SourceChunk]) -> str:
    context = "\n".join(f"[{source.id}] {source.source} ({source.category}, score={source.score:.4f}): {source.text}" for source in sources)
    return (
        f"{DEFAULT_PROMPT}\n{language_instruction(request.language)}\n\n"
        f"Context RAG:\n{context}\n\n"
        f"Resum de sessio:\n{session_to_query(request.session, request.language)}\n\n"
        "Resposta:"
    )


def call_ollama(prompt: str) -> tuple[str, str, int]:
    base_url = os.environ.get("OLLAMA_BASE_URL", "https://ollama.com").rstrip("/")
    model = os.environ.get("OLLAMA_MODEL", "qwen3-coder-next")
    api_key = os.environ.get("OLLAMA_API_KEY", "")
    endpoint = f"{base_url}/api/chat" if not base_url.endswith("/api") else f"{base_url}/chat"
    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
    payload = {
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "stream": False,
        "think": False,
        "options": {"temperature": 0.2, "num_predict": 220},
    }
    started = time.perf_counter()
    response = requests.post(endpoint, json=payload, headers=headers, timeout=int(os.environ.get("OLLAMA_TIMEOUT", "60")))
    latency = int((time.perf_counter() - started) * 1000)
    if response.status_code >= 400:
        raise HTTPException(status_code=502, detail=f"Ollama HTTP {response.status_code}: {response.text[:500]}")
    data: dict[str, Any] = response.json()
    answer = ((data.get("message") or {}).get("content") or data.get("response") or "").strip()
    if not answer:
        raise HTTPException(status_code=502, detail="Ollama returned an empty answer.")
    return answer, model, latency


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/rag/session-summary", response_model=RagResponse)
def session_summary(request: RagRequest) -> RagResponse:
    vectorstore = get_vectorstore()
    query = session_to_query(request.session, request.language)
    retrieved = vectorstore.similarity_search_with_score(query, k=request.top_k)
    sources = []
    for document, distance in retrieved:
        metadata = document.metadata or {}
        sources.append(
            SourceChunk(
                id=str(metadata.get("id", metadata.get("doc_id", ""))),
                source=str(metadata.get("source", "unknown")),
                category=str(metadata.get("category", "unknown")),
                language=str(metadata.get("language", "ca")),
                chunk_id=int(metadata.get("chunk_id", 0)),
                score=round(max(0.0, 1.0 - float(distance)), 4),
                text=document.page_content[:1200],
            )
        )
    answer, model, latency = call_ollama(build_prompt(request, sources))
    return RagResponse(
        title="Interpretacio post sessio",
        answer=answer,
        provider="remote-rag",
        model=model,
        latency_ms=latency,
        sources=sources,
    )
