# SmarTrain RAG implementation guide

## Estat implementat

- Base documental principal a `ml/rag/data/knowledge_base.jsonl`.
- L'app Android empaqueta aquest JSONL com a asset i el carrega amb `SessionRagRecommender.initialize(...)`.
- S'ha eliminat la dependencia funcional dels documents hardcoded en Kotlin per a la base catalana.
- Script offline per construir index vectorial:
  - `ml/rag/scripts/build_vector_index.py`
  - ChromaDB local per defecte.
  - Pinecone opcional, nomes via entorn/backend.
- Script per exportar embeddings a TensorFlow Projector:
  - `ml/rag/scripts/export_projector.py`
  - `ml/rag/results/projector/vectors.tsv`
  - `ml/rag/results/projector/metadata.tsv`
  - `ml/rag/results/projector/pca_preview.png`
- Backend intermedi FastAPI:
  - `ml/rag/scripts/rag_backend.py`
  - endpoint `POST /rag/session-summary`
  - consulta ChromaDB, genera resposta amb Ollama i retorna chunks, scores i latencia.
- Android pot usar un `RemoteRagGenerator` contra el backend remot.
- La fitxa de sessio mostra chunks font i scores si el backend els retorna.
- Configuracio segura amb `.env` i `.env.example`.
- Model LLM final documentat: `qwen3-coder-next` via Ollama Cloud.

## Instal·lacio

Crear entorn Python i instal·lar dependencies:

```powershell
pip install -r ml/requirements.txt
```

Crear `.env` a partir de `.env.example`:

```powershell
copy .env.example .env
```

Configurar com a minim:

```env
OLLAMA_BASE_URL=https://ollama.com
OLLAMA_API_KEY=...
OLLAMA_MODEL=qwen3-coder-next
SMARTRAIN_RAG_VECTORSTORE=chroma
SMARTRAIN_RAG_CHROMA_DIR=ml/rag/chroma_db/smartrain
SMARTRAIN_RAG_COLLECTION=smartrain_rag
```

No posar claus dins notebooks, codi Kotlin ni fitxers versionats.

## Construir l'index ChromaDB

```powershell
python ml/rag/scripts/build_vector_index.py --store chroma --embedding-provider ollama --embedding-model nomic-embed-text --reset
```

Parametres per defecte:

- `chunk_size=700`
- `chunk_overlap=90`
- metadata: `id`, `source`, `category`, `language`, `chunk_id`

Alternativa lleugera:

```powershell
python ml/rag/scripts/build_vector_index.py --store chroma --embedding-provider sentence-transformers --embedding-model sentence-transformers/all-MiniLM-L6-v2 --reset
```

## Pinecone opcional

Pinecone queda pensat per demo cloud o backend compartit. No s'ha d'accedir mai directament des de l'app Android.

```powershell
python ml/rag/scripts/build_vector_index.py --store pinecone --embedding-provider sentence-transformers
```

Requereix `PINECONE_API_KEY` al `.env`.

## Backend RAG

Arrencar el backend:

```powershell
uvicorn ml.rag.scripts.rag_backend:app --reload --host 0.0.0.0 --port 8000
```

Endpoint principal:

```http
POST /rag/session-summary
```

Cos esperat:

```json
{
  "language": "ca",
  "session": {
    "dominantActivity": "Alta intensitat",
    "avgMlConfidence": 0.82,
    "mlPredictionCount": 24,
    "highIntensityCount": 12,
    "durationSeconds": 1800,
    "distanceMetres": 3200.0
  }
}
```

Resposta:

- `title`
- `answer`
- `provider`
- `model`
- `latency_ms`
- `sources[]` amb `source`, `category`, `chunk_id`, `score`, `text`

## Android

A la pantalla de perfil:

- Activar `Backend RAG remot`.
- URL emulador Android: `http://10.0.2.2:8000`
- URL mobil fisic: IP LAN del PC, per exemple `http://192.168.1.50:8000`

Si el backend remot falla, SmarTrain conserva fallback local/rules segons configuracio.

## Export TensorFlow Projector

```powershell
python ml/rag/scripts/export_projector.py --store chroma
```

Obrir `https://projector.tensorflow.org` i carregar:

- vectors: `ml/rag/results/projector/vectors.tsv`
- metadata: `ml/rag/results/projector/metadata.tsv`

La preview local queda a:

```text
ml/rag/results/projector/pca_preview.png
```

## Comparar embeddings

```powershell
python ml/rag/scripts/compare_embedding_models.py
```

Compara:

- `nomic-embed-text`
- `sentence-transformers/all-MiniLM-L6-v2`

Sortida:

```text
ml/rag/results/embedding_model_comparison.json
```

## Avaluacio RAG

Ja hi ha equivalents interns a RAGAS:

- `ml/rag/scripts/evaluate_rag.py`
- `ml/rag/scripts/evaluate_ollama_models.py`
- `ml/rag/scripts/compare_generation_models.py`

Metrics actuals:

- hit rate de fonts
- MRR
- grounded overlap
- cobertura de termes esperats
- risc de dades inventades
- utilitat de recomanacio
- qualitat linguistica

## Pendent per completar l'apartat RAG

1. Executar `build_vector_index.py` en un entorn amb Ollama i `nomic-embed-text` disponible.
2. Executar `export_projector.py` i guardar captures del projector 3D/PCA per a la memoria.
3. Executar `compare_embedding_models.py` i decidir si es manté `nomic-embed-text` o si MiniLM dona millor recuperacio.
4. Arrencar `rag_backend.py` i provar `/rag/session-summary` amb sessions reals.
5. Activar `Backend RAG remot` a l'app i validar una sessio de punta a punta.
6. Afegir captures de la fitxa de sessio amb chunks i scores.
7. Regenerar comparativa final de models LLM deixant `qwen3-coder-next` com a guanyador documentat.
8. Revisar que `.env` no queda versionat i que no hi ha API keys en notebooks.
9. Afegir al `PROJECT_REPORT.md` els resultats finals: index, embedding model, top_k, LLM final, latencia i exemples de resposta.
10. Si es presenta Pinecone, explicar que nomes s'usa des del backend i que Android no conte claus.
