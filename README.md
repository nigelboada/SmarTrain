# SmarTrain

SmarTrain es una aplicacio Android per monitoritzar sessions esportives i enriquir-les amb analisi ML local i recomanacions post-sessio basades en RAG.

L'app captura accelerometre i localitzacio, executa un model TensorFlow Lite al dispositiu, guarda sessions amb Room, sincronitza amb Firebase Firestore i pot generar un resum IA amb Ollama Cloud, amb un backend RAG propi o amb fallback local per regles.

## Estat del projecte

| Bloc | Estat |
| --- | --- |
| App Android | Implementada amb Jetpack Compose, Room, Firebase Auth/Firestore i foreground service |
| Classificador ML | Integrat amb TensorFlow Lite |
| RAG local | Integrat i funcional offline |
| Backend RAG | FastAPI + ChromaDB + Ollama Cloud validat en mobil fisic |
| IA generativa | Integrada via Ollama `/api/chat` i backend RAG |
| Model generatiu seleccionat | `qwen3-coder-next` via Ollama Cloud |
| Fallback | Regles locals persistides amb la sessio |

## Documentacio principal

Documentacio tecnica:

```text
docs/PROJECT_REPORT.md
docs/RAG_IMPLEMENTATION_GUIDE.md
ML_EXPERIMENTS.md
```

Inclou:

- arquitectura del projecte;
- experimentacio ML amb UCI HAR;
- comparacio del model TensorFlow Lite;
- disseny i avaluacio RAG;
- comparativa de 6 models generatius locals/cloud;
- decisio final del model IA;
- estat de la integracio i punts pendents.

## Execucio Completa

### 1. Preparar Python i RAG

```powershell
pip install -r ml/requirements.txt
```

Configurar `.env`:

```env
OLLAMA_BASE_URL=https://ollama.com
OLLAMA_API_KEY=...
OLLAMA_MODEL=qwen3-coder-next
OLLAMA_TIMEOUT=60

SMARTRAIN_RAG_EMBED_BASE_URL=http://127.0.0.1:11434
SMARTRAIN_RAG_EMBED_MODEL=nomic-embed-text
SMARTRAIN_RAG_VECTORSTORE=chroma
SMARTRAIN_RAG_CHROMA_DIR=ml/rag/chroma_db/smartrain
SMARTRAIN_RAG_COLLECTION=smartrain_rag
SMARTRAIN_RAG_TOP_K=3
```

Ollama local ha de tenir el model d'embeddings:

```powershell
ollama pull nomic-embed-text
```

Reconstruir l'index si cal:

```powershell
python ml/rag/scripts/build_vector_index.py --store chroma --embedding-provider ollama --embedding-model nomic-embed-text --reset
```

### 2. Arrencar Backend RAG

```powershell
python -m uvicorn ml.rag.scripts.rag_backend:app --host 0.0.0.0 --port 8000
```

En mobil fisic, el backend s'ha d'obrir amb la IP LAN del PC. En aquest entorn validat:

```text
http://192.168.1.14:8000
```

Proves rapides:

```text
http://192.168.1.14:8000/health
http://192.168.1.14:8000/rag/demo-session-summary
```

### 3. Executar l'app

Compilar l'app:

```powershell
.\gradlew.bat :app:assembleDebug
```

Instal.lar-la o executar-la des d'Android Studio. A `Perfil > Assistent IA` es poden triar tres modes:

- `Resum local`: sempre funciona, sense internet.
- `qwen3-coder-next`: usa Ollama Cloud directe i requereix API key dins l'app.
- `Backend RAG`: usa FastAPI + ChromaDB + `qwen3-coder-next`, retorna fonts, chunks i scores.

Executar tests unitaris:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

### 4. Reproduir experiments ML/RAG

Reproduir la comparativa generativa:

```powershell
$env:OLLAMA_API_KEY="..."
python ml/rag/scripts/compare_generation_models.py --timeout 180
```

Comparar embeddings:

```powershell
python ml/rag/scripts/compare_embedding_models.py --ollama-base-url http://127.0.0.1:11434
```

Export TensorFlow Projector:

```powershell
python ml/rag/scripts/export_projector.py --store chroma
```

## Resultats principals

Model ML local seleccionat:

| Model | Accuracy | F1 weighted | Mida TFLite |
| --- | ---: | ---: | ---: |
| `cnn_deep` | 96,01% | 96,02% | 55.208 bytes |

Model generatiu seleccionat:

| Model | Proveidor | Tasques OK | Latencia mitjana | Decisio |
| --- | --- | ---: | ---: | --- |
| `qwen3-coder-next` | Ollama Cloud | 10/10 | 3.378,32 ms | Seleccionat |

El fallback local per regles continua sent obligatori per garantir que l'app funciona sense connexio o si Ollama falla.

## Validacio End-to-End

Validat:

- TFLite integrat a Android.
- Flux sensor -> model ML -> resum sessio -> persistencia.
- Ollama Cloud directe amb `qwen3-coder-next`.
- Backend RAG en mobil fisic amb `http://192.168.1.14:8000`.
- Fonts RAG clicables a la fitxa de sessio.
- Tests unitaris Android en verd.
