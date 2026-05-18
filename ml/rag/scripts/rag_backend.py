from __future__ import annotations

import os
import time
from pathlib import Path
from typing import Any

import requests
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

try:
    from rag_common import CHROMA_DIR, COLLECTION_NAME, load_env_file
except ModuleNotFoundError:
    from .rag_common import CHROMA_DIR, COLLECTION_NAME, load_env_file


load_env_file()

APP_TITLE = "SmarTrain RAG Backend"
DEFAULT_PROMPT = """Ets l'assistent de SmarTrain.
Respon nomes amb el context recuperat i les dades de la sessio.
Si no hi ha informacio suficient al context, digues-ho clarament.
No inventis metriques ni diagnostics medics.
Inclou una recomanacio accionable i prudent.
No escriguis raonament intern, nomes la resposta final."""


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


class GuidedRagRequest(RagRequest):
    question_id: str = "improve_next"


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


class RetrieveResponse(BaseModel):
    query: str
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
    ollama_base_url = os.environ.get("SMARTRAIN_RAG_EMBED_BASE_URL", os.environ.get("OLLAMA_BASE_URL", "http://127.0.0.1:11434"))
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
        "en": "Answer exclusively in English.",
        "es": "Responde exclusivamente en castellano.",
        "zh": "\u8bf7\u53ea\u7528\u4e2d\u6587\u56de\u7b54\u3002",
        "ca": "Respon exclusivament en catala. No facis servir castella.",
    }.get(language, "Respon en catala.")


def build_prompt(request: RagRequest, sources: list[SourceChunk]) -> str:
    context = "\n".join(f"[{source.id}] {source.source} ({source.category}, score={source.score:.4f}): {source.text}" for source in sources)
    return (
        f"{DEFAULT_PROMPT}\n{language_instruction(request.language)}\n\n"
        f"Context RAG:\n{context}\n\n"
        f"Resum de sessio:\n{session_to_query(request.session, request.language)}\n\n"
        "Resposta:"
    )


def guided_question_text(question_id: str, language: str) -> str:
    labels = {
        "improve_next": {
            "ca": "Com puc millorar la propera sessio?",
            "en": "How can I improve the next session?",
            "es": "Como puedo mejorar la proxima sesion?",
            "zh": "\u6211\u5982\u4f55\u6539\u8fdb\u4e0b\u4e00\u6b21\u8bad\u7ec3\uff1f",
        },
        "why_recommendation": {
            "ca": "Per que recomanes aixo?",
            "en": "Why do you recommend this?",
            "es": "Por que recomiendas esto?",
            "zh": "\u4f60\u4e3a\u4ec0\u4e48\u8fd9\u6837\u5efa\u8bae\uff1f",
        },
        "prediction_limits": {
            "ca": "Quines limitacions te aquesta prediccio?",
            "en": "What limitations does this prediction have?",
            "es": "Que limitaciones tiene esta prediccion?",
            "zh": "\u8fd9\u4e2a\u9884\u6d4b\u6709\u54ea\u4e9b\u5c40\u9650\uff1f",
        },
        "recovery": {
            "ca": "Quina recuperacio em convé despres d'aquesta sessio?",
            "en": "What recovery is appropriate after this session?",
            "es": "Que recuperacion me conviene despues de esta sesion?",
            "zh": "\u8fd9\u6b21\u8bad\u7ec3\u540e\u6211\u5e94\u8be5\u5982\u4f55\u6062\u590d\uff1f",
        },
        "confidence_meaning": {
            "ca": "Que significa la confianca del model?",
            "en": "What does the model confidence mean?",
            "es": "Que significa la confianza del modelo?",
            "zh": "\u6a21\u578b\u7f6e\u4fe1\u5ea6\u610f\u5473\u7740\u4ec0\u4e48\uff1f",
        },
        "high_intensity": {
            "ca": "Com he d'interpretar els blocs d'alta intensitat?",
            "en": "How should I interpret high-intensity blocks?",
            "es": "Como debo interpretar los bloques de alta intensidad?",
            "zh": "\u6211\u5e94\u8be5\u5982\u4f55\u7406\u89e3\u9ad8\u5f3a\u5ea6\u7247\u6bb5\uff1f",
        },
        "sensor_quality": {
            "ca": "Com puc millorar la qualitat dels sensors del mobil?",
            "en": "How can I improve the phone sensor quality?",
            "es": "Como puedo mejorar la calidad de los sensores del movil?",
            "zh": "\u5982\u4f55\u63d0\u9ad8\u624b\u673a\u4f20\u611f\u5668\u8d28\u91cf\uff1f",
        },
        "phone_position": {
            "ca": "On hauria de portar el mobil?",
            "en": "Where should I carry the phone?",
            "es": "Donde deberia llevar el movil?",
            "zh": "\u624b\u673a\u5e94\u8be5\u653e\u5728\u54ea\u91cc\uff1f",
        },
        "warmup_first_seconds": {
            "ca": "Els primers segons son fiables?",
            "en": "Are the first seconds reliable?",
            "es": "Son fiables los primeros segundos?",
            "zh": "\u524d\u51e0\u79d2\u53ef\u9760\u5417\uff1f",
        },
        "repeat_session": {
            "ca": "Hauria de repetir aquesta sessio?",
            "en": "Should I repeat this session?",
            "es": "Deberia repetir esta sesion?",
            "zh": "\u6211\u5e94\u8be5\u91cd\u590d\u8fd9\u6b21\u8bad\u7ec3\u5417\uff1f",
        },
        "load_control": {
            "ca": "Com puc controlar la carrega?",
            "en": "How can I control training load?",
            "es": "Como puedo controlar la carga?",
            "zh": "\u5982\u4f55\u63a7\u5236\u8bad\u7ec3\u8d1f\u8377\uff1f",
        },
        "next_training_focus": {
            "ca": "Quin focus hauria de tenir el proper entrenament?",
            "en": "What should the next training focus be?",
            "es": "Que enfoque deberia tener el proximo entreno?",
            "zh": "\u4e0b\u6b21\u8bad\u7ec3\u5e94\u5173\u6ce8\u4ec0\u4e48\uff1f",
        },
        "low_confidence_causes": {
            "ca": "Que pot causar baixa confianca?",
            "en": "What can cause low confidence?",
            "es": "Que puede causar baja confianza?",
            "zh": "\u4ec0\u4e48\u4f1a\u5bfc\u81f4\u4f4e\u7f6e\u4fe1\u5ea6\uff1f",
        },
        "football_limits": {
            "ca": "Que limita l'us en futbol?",
            "en": "What limits football use?",
            "es": "Que limita el uso en futbol?",
            "zh": "\u8db3\u7403\u573a\u666f\u6709\u4ec0\u4e48\u9650\u5236\uff1f",
        },
        "data_needed": {
            "ca": "Quines dades caldrien per millorar el model?",
            "en": "What data would improve the model?",
            "es": "Que datos mejorarian el modelo?",
            "zh": "\u54ea\u4e9b\u6570\u636e\u53ef\u4ee5\u6539\u8fdb\u6a21\u578b\uff1f",
        },
        "dominant_activity": {
            "ca": "Que vol dir l'activitat dominant?",
            "en": "What does dominant activity mean?",
            "es": "Que significa la actividad dominante?",
            "zh": "\u4e3b\u8981\u6d3b\u52a8\u662f\u4ec0\u4e48\u610f\u601d\uff1f",
        },
        "rest_interpretation": {
            "ca": "Com interpreto una sessio suau o de repos?",
            "en": "How do I read a light or rest session?",
            "es": "Como interpreto una sesion suave o de reposo?",
            "zh": "\u5982\u4f55\u89e3\u8bfb\u8f7b\u5ea6\u6216\u4f11\u606f\u8bad\u7ec3\uff1f",
        },
        "intensity_ratio": {
            "ca": "Hi ha massa alta intensitat?",
            "en": "Is there too much high intensity?",
            "es": "Hay demasiada alta intensidad?",
            "zh": "\u9ad8\u5f3a\u5ea6\u662f\u5426\u8fc7\u591a\uff1f",
        },
        "model_metrics": {
            "ca": "Quines metriques te el model final?",
            "en": "What metrics does the final model have?",
            "es": "Que metricas tiene el modelo final?",
            "zh": "\u6700\u7ec8\u6a21\u578b\u7684\u6307\u6807\u662f\u4ec0\u4e48\uff1f",
        },
        "rag_sources": {
            "ca": "D'on surten les fonts del RAG?",
            "en": "Where do RAG sources come from?",
            "es": "De donde salen las fuentes del RAG?",
            "zh": "RAG \u6765\u6e90\u4ece\u54ea\u91cc\u6765\uff1f",
        },
        "demo_best_mode": {
            "ca": "Quin mode conve per a una demo?",
            "en": "Which mode is best for a demo?",
            "es": "Que modo conviene para una demo?",
            "zh": "\u6f14\u793a\u65f6\u6700\u9002\u5408\u7528\u54ea\u79cd\u6a21\u5f0f\uff1f",
        },
        "practical_tip": {
            "ca": "Dona'm un consell practic per avui",
            "en": "Give me one practical tip for today",
            "es": "Dame un consejo practico para hoy",
            "zh": "\u7ed9\u6211\u4e00\u4e2a\u4eca\u5929\u7684\u5b9e\u7528\u5efa\u8bae",
        },
    }
    return labels.get(question_id, labels["improve_next"]).get(language, labels["improve_next"]["ca"])


def build_guided_prompt(request: GuidedRagRequest, sources: list[SourceChunk]) -> str:
    question = guided_question_text(request.question_id, request.language)
    context = "\n".join(f"[{source.id}] {source.source} ({source.category}, score={source.score:.4f}): {source.text}" for source in sources)
    return (
        f"{DEFAULT_PROMPT}\n{language_instruction(request.language)}\n\n"
        "Estas responent una pregunta guiada post-sessio. "
        "Respon de forma breu, practica i fonamentada en les fonts recuperades. "
        "No facis diagnostics medics ni inventis dades que no apareguin a la sessio.\n\n"
        f"Pregunta de l'usuari:\n{question}\n\n"
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
    try:
        response = requests.post(endpoint, json=payload, headers=headers, timeout=int(os.environ.get("OLLAMA_TIMEOUT", "60")))
    except requests.RequestException as exc:
        raise HTTPException(status_code=502, detail=f"Ollama request failed: {exc}") from exc
    latency = int((time.perf_counter() - started) * 1000)
    if response.status_code >= 400:
        raise HTTPException(status_code=502, detail=f"Ollama HTTP {response.status_code}: {response.text[:500]}")
    data: dict[str, Any] = response.json()
    answer = ((data.get("message") or {}).get("content") or data.get("response") or "").strip()
    if not answer:
        raise HTTPException(status_code=502, detail="Ollama returned an empty answer.")
    return answer, model, latency


def retrieve_sources(request: RagRequest, query_override: str | None = None) -> tuple[str, list[SourceChunk]]:
    vectorstore = get_vectorstore()
    query = query_override or session_to_query(request.session, request.language)
    retrieved = vectorstore.similarity_search_with_score(query, k=max(request.top_k * 8, 32))
    scored_sources = []
    for document, distance in retrieved:
        metadata = document.metadata or {}
        score = adjusted_score(metadata, distance)
        scored_sources.append(
            SourceChunk(
                id=str(metadata.get("id", metadata.get("doc_id", ""))),
                source=str(metadata.get("source", "unknown")),
                category=str(metadata.get("category", "unknown")),
                language=str(metadata.get("language", "ca")),
                chunk_id=int(metadata.get("chunk_id", 0)),
                score=round(score, 4),
                text=document.page_content[:1200],
            )
        )
    sources = select_diverse_sources(scored_sources, request.top_k)
    return query, sources


def select_diverse_sources(sources: list[SourceChunk], top_k: int) -> list[SourceChunk]:
    selected: list[SourceChunk] = []
    anonymous_count = 0
    for source in sorted(sources, key=lambda item: item.score, reverse=True):
        if source.category == "anonymous_sessions" and anonymous_count >= 1:
            continue
        selected.append(source)
        if source.category == "anonymous_sessions":
            anonymous_count += 1
        if len(selected) >= top_k:
            return selected
    return selected


def adjusted_score(metadata: dict[str, Any], distance: float) -> float:
    base_score = max(0.0, 1.0 - float(distance))
    category = str(metadata.get("category", ""))
    source = str(metadata.get("source", ""))
    boost = {
        "training_recommendation": 0.08,
        "model_interpretation": 0.04,
        "ml_experiment": 0.02,
        "rag_design": -0.03,
        "project_report": -0.06,
        "anonymous_sessions": -0.08,
    }.get(category, 0.0)
    if source == "sessions.jsonl":
        boost -= 0.05
    return max(0.0, base_score + boost)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/rag/retrieve", response_model=RetrieveResponse)
def retrieve(request: RagRequest) -> RetrieveResponse:
    query, sources = retrieve_sources(request)
    return RetrieveResponse(query=query, sources=sources)


@app.post("/rag/session-summary", response_model=RagResponse)
def session_summary(request: RagRequest) -> RagResponse:
    _, sources = retrieve_sources(request)
    answer, model, latency = call_ollama(build_prompt(request, sources))
    return RagResponse(
        title="Interpretacio post sessio",
        answer=answer,
        provider="remote-rag",
        model=model,
        latency_ms=latency,
        sources=sources,
    )


@app.post("/rag/guided-question", response_model=RagResponse)
def guided_question(request: GuidedRagRequest) -> RagResponse:
    allowed_questions = {
        "improve_next",
        "why_recommendation",
        "prediction_limits",
        "recovery",
        "confidence_meaning",
        "high_intensity",
        "sensor_quality",
        "phone_position",
        "warmup_first_seconds",
        "repeat_session",
        "load_control",
        "next_training_focus",
        "low_confidence_causes",
        "football_limits",
        "data_needed",
        "dominant_activity",
        "rest_interpretation",
        "intensity_ratio",
        "model_metrics",
        "rag_sources",
        "demo_best_mode",
        "practical_tip",
    }
    if request.question_id not in allowed_questions:
        raise HTTPException(status_code=400, detail="Unknown guided question.")
    question = guided_question_text(request.question_id, request.language)
    query = f"{question} {session_to_query(request.session, request.language)}"
    _, sources = retrieve_sources(request, query_override=query)
    answer, model, latency = call_ollama(build_guided_prompt(request, sources))
    return RagResponse(
        title=question,
        answer=answer,
        provider="remote-rag",
        model=model,
        latency_ms=latency,
        sources=sources,
    )


@app.get("/rag/demo-session-summary", response_model=RagResponse)
def demo_session_summary() -> RagResponse:
    request = RagRequest(
        language="ca",
        top_k=3,
        session=SessionPayload(
            dominantActivity="Alta intensitat",
            avgMlConfidence=0.82,
            mlPredictionCount=24,
            highIntensityCount=12,
            durationSeconds=1800,
            distanceMetres=3200.0,
        ),
    )
    return session_summary(request)


@app.get("/rag/demo-guided-question", response_model=RagResponse)
def demo_guided_question(question_id: str = "improve_next") -> RagResponse:
    request = GuidedRagRequest(
        language="ca",
        top_k=3,
        question_id=question_id,
        session=SessionPayload(
            dominantActivity="Alta intensitat",
            avgMlConfidence=0.82,
            mlPredictionCount=24,
            highIntensityCount=12,
            durationSeconds=1800,
            distanceMetres=3200.0,
        ),
    )
    return guided_question(request)
