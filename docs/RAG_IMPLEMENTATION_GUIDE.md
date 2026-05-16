# SmarTrain RAG Implementation Guide

## Estat Implementat

- Base documental principal a `ml/rag/data/knowledge_base.jsonl`.
- L'app Android empaqueta aquest JSONL com a asset i el carrega amb `SessionRagRecommender.initialize(...)`.
- Script offline per construir index vectorial amb ChromaDB:
  - `ml/rag/scripts/build_vector_index.py`
  - index validat a `ml/rag/chroma_db/smartrain`
  - `raw_documents: 17`, `chunks: 42`, `embedding_dimension: 768`
- Script per exportar embeddings a TensorFlow Projector:
  - `ml/rag/scripts/export_projector.py`
  - `ml/rag/results/projector/vectors.tsv`
  - `ml/rag/results/projector/metadata.tsv`
  - `ml/rag/results/projector/pca_preview.png`
- Backend intermedi FastAPI:
  - `ml/rag/scripts/rag_backend.py`
  - `POST /rag/retrieve` per validar recuperacio sense LLM/API key
  - `POST /rag/session-summary` per recuperar chunks, generar resposta amb Ollama i retornar fonts, scores i latencia
- Android pot usar `RemoteRagGenerator` contra el backend remot.
- La fitxa de sessio mostra resposta RAG, proveidor/model, latencia, fallback i chunks font.
- Model generatiu final documentat: `qwen3-coder-next` via Ollama Cloud.

## Instal·lacio

```powershell
pip install -r ml/requirements.txt
```

Configuracio minima a `.env`:

```env
OLLAMA_BASE_URL=https://ollama.com
OLLAMA_API_KEY=...
OLLAMA_MODEL=qwen3-coder-next
SMARTRAIN_RAG_EMBED_BASE_URL=http://127.0.0.1:11434
SMARTRAIN_RAG_VECTORSTORE=chroma
SMARTRAIN_RAG_CHROMA_DIR=ml/rag/chroma_db/smartrain
SMARTRAIN_RAG_COLLECTION=smartrain_rag
```

`SMARTRAIN_RAG_EMBED_BASE_URL` permet usar `nomic-embed-text` local per Chroma mentre `OLLAMA_BASE_URL` apunta a Ollama Cloud per generar text.

## Construir L'index

```powershell
python ml/rag/scripts/build_vector_index.py --store chroma --embedding-provider ollama --embedding-model nomic-embed-text --reset
```

Parametres:

- `chunk_size=700`
- `chunk_overlap=90`
- metadata: `id`, `source`, `category`, `language`, `chunk_id`

## Backend RAG

Arrencar backend:

```powershell
uvicorn ml.rag.scripts.rag_backend:app --reload --host 0.0.0.0 --port 8000
```

Validar recuperacio sense LLM:

```http
POST /rag/retrieve
```

Generar resum:

```http
POST /rag/session-summary
```

Cos esperat:

```json
{
  "language": "ca",
  "top_k": 3,
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
- `sources[]` amb `id`, `source`, `category`, `chunk_id`, `score`, `text`

Validacio feta:

- `/rag/retrieve`: 200 OK amb fonts de Chroma.
- `/rag/session-summary`: 200 OK amb Ollama local `gemma3:1b`, model retornat i latencia.
- Ollama Cloud no s'ha pogut validar en aquest entorn per error DNS resolent `ollama.com`.

## Android

A la pantalla de perfil:

- `Resum local`: opcio segura per defecte, sense internet ni API key.
- `qwen3-coder-next`: usa Ollama Cloud directe. Requereix API key d'Ollama Cloud.
- `Backend RAG`: usa FastAPI + ChromaDB. Permet veure chunks i scores a la fitxa de sessio.

Per al mode `Backend RAG`:

- URL mobil fisic configurada per a aquest entorn: `http://192.168.1.75:8000`.
- URL emulador Android, si algun dia es fa servir: `http://10.0.2.2:8000`.
- Si la IP del PC canvia, cal actualitzar el camp URL del backend a la pantalla de perfil.

Si el backend remot o Ollama fallen, SmarTrain guarda fallback local per regles i mostra el motiu a la fitxa de sessio.

## TensorFlow Projector

```powershell
python ml/rag/scripts/export_projector.py --store chroma
```

Carregar a `https://projector.tensorflow.org`:

- vectors: `ml/rag/results/projector/vectors.tsv`
- metadata: `ml/rag/results/projector/metadata.tsv`

Validat manualment:

- 42 punts carregats.
- 768 dimensions.
- PCA, t-SNE i UMAP visibles.
- Color by `category` i `source` funcional.

## Comparativa D'embeddings

```powershell
python ml/rag/scripts/compare_embedding_models.py --ollama-base-url http://127.0.0.1:11434
```

Resultat:

| Model | Hit@6 | MRR | Decisio |
| --- | ---: | ---: | --- |
| `nomic-embed-text` | 0,833 | 0,708 | Seleccionat |
| `sentence-transformers/all-MiniLM-L6-v2` | 0,833 | 0,694 | Alternativa viable |

Sortida:

```text
ml/rag/results/embedding_model_comparison.json
```

## Estat Final I Pendent Manual

Fet:

- ChromaDB creat amb `nomic-embed-text`.
- Export Projector validat amb les captures aportades.
- Comparativa d'embeddings executada.
- Backend FastAPI corregit i validat amb recuperacio i generacio local.
- Android corregit per marcar fallback remot i mostrar `backend RAG` a la fitxa.
- Tests Android RAG en verd amb `.\gradlew.bat :app:testDebugUnitTest`.

Pendent manual:

1. Validar `/rag/session-summary` amb `qwen3-coder-next` via Ollama Cloud quan `ollama.com` resolgui DNS i la API key sigui valida.
2. Arrencar backend amb `uvicorn` i activar `Backend RAG remot` a l'app.
3. Fer una sessio real o de demo i comprovar la fitxa de sessio amb resposta RAG, chunks i scores.
4. Afegir captura final de la fitxa de sessio al report.
5. Revisar que `.env` no queda versionat i que no hi ha API keys en notebooks.
