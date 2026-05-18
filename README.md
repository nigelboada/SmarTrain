# SmarTrain

SmarTrain és una aplicació Android per registrar sessions esportives, classificar activitat amb un model ML al dispositiu i generar una interpretació post-sessió amb IA. El sistema combina TensorFlow Lite, Room, Firebase i un backend RAG propi amb FastAPI, ChromaDB i Ollama.

## Estat del projecte

| Àrea | Estat |
| --- | --- |
| App Android | Funcional en mòbil físic |
| Model ML | Integrat amb TensorFlow Lite |
| Backend | Integrat amb FastAPI |
| RAG | ChromaDB + embeddings `nomic-embed-text` |
| IA generativa | `qwen3-coder-next` via Ollama |
| Mode segur | Resum local si falla la IA |

## Documentació

- `README.md`: execució del sistema complet i ús de l'app.
- `ML_EXPERIMENTS.md`: document final d'experimentació ML i RAG.

La documentació del projecte s'ha unificat en aquests dos fitxers per evitar duplicats.

## Requisits

- Android Studio.
- JDK compatible amb Gradle del projecte.
- Python 3.10 o superior.
- Ollama local amb el model d'embeddings `nomic-embed-text`.
- Clau d'Ollama Cloud per al model `qwen3-coder-next`.

## Configuració

Crea o revisa el fitxer `.env` a l'arrel del projecte. No s'ha de pujar a Git.

```env
OLLAMA_BASE_URL=https://ollama.com
OLLAMA_MODEL=qwen3-coder-next
OLLAMA_API_KEY=la_teva_clau
OLLAMA_TIMEOUT=60

SMARTRAIN_RAG_EMBED_BASE_URL=http://127.0.0.1:11434
SMARTRAIN_RAG_EMBED_MODEL=nomic-embed-text
SMARTRAIN_RAG_VECTORSTORE=chroma
SMARTRAIN_RAG_CHROMA_DIR=ml/rag/chroma_db/smartrain
SMARTRAIN_RAG_COLLECTION=smartrain_rag
SMARTRAIN_RAG_TOP_K=3
```

La generació de text usa Ollama Cloud. Els embeddings del RAG es generen localment amb Ollama perquè ChromaDB pugui recuperar fragments del corpus.

## Execució del RAG

Instal·la les dependències Python:

```powershell
pip install -r ml/rag/requirements.txt
```

Comprova que Ollama local tingui el model d'embeddings:

```powershell
ollama list
ollama pull nomic-embed-text
```

Construeix l'índex vectorial:

```powershell
python ml/rag/scripts/build_vector_index.py
```

Arrenca el backend accessible des del mòbil:

```powershell
uvicorn ml.rag.scripts.rag_backend:app --host 0.0.0.0 --port 8000
```

Des del mòbil, valida:

```text
http://192.168.1.14:8000/health
http://192.168.1.14:8000/rag/demo-session-summary
http://192.168.1.14:8000/rag/demo-guided-question?question_id=improve_next
```

La IP pot canviar segons la xarxa. Si canvia, actualitza-la a Perfil > Assistent IA > Backend RAG.

## Execució de l'app

1. Obre el projecte amb Android Studio.
2. Connecta el mòbil físic amb depuració USB.
3. Executa l'app.
4. Ves a Perfil > Assistent IA i tria un mode:
   - `Resum local`: sempre funciona i no requereix xarxa.
   - `qwen3-coder-next`: genera el resum directament amb el model cloud.
   - `Backend RAG`: usa el backend propi, recupera fonts i mostra chunks/scores.
5. Guarda els canvis.
6. Fes una sessió i finalitza-la.
7. Obre el detall de sessió per veure la interpretació post-sessió.

En mode Backend RAG, el detall de sessió mostra:

- resposta generada;
- model i latència;
- fragments de context recuperats;
- scores de recuperació;
- fitxes de chunks clicables;
- FAQ RAG post-sessió amb preguntes guiades.

## Funcionalitat RAG a l'app

El RAG no substitueix el model ML. El model ML classifica blocs de moviment i el RAG transforma aquestes prediccions en una explicació útil per a l'usuari.

Funcionalitats visibles:

- interpretació post-sessió amb context recuperat;
- fonts/chunks clicables per veure el text complet;
- FAQ RAG disponible des del detall de sessió encara que el resum principal s'hagi creat amb mode local o cloud, sempre que el backend estigui actiu;
- preguntes guiades post-sessió:
  - com millorar la propera sessió;
  - per què es recomana una acció;
  - quines limitacions té la predicció;
  - recuperació recomanada;
  - significat de la confiança;
  - interpretació dels blocs d'alta intensitat.

Aquesta és la funcionalitat diferencial del RAG propi respecte del mode cloud directe: no només genera text, sinó que pot mostrar d'on surt la resposta i respondre preguntes acotades amb el mateix context documental.

## Experiments

Model ML:

```powershell
python ml/scripts/preprocess.py
python ml/scripts/train_model.py
python ml/scripts/convert_to_tflite.py
```

RAG:

```powershell
python ml/rag/scripts/build_knowledge_base.py
python ml/rag/scripts/build_vector_index.py
python ml/rag/scripts/compare_embedding_models.py
python ml/rag/scripts/compare_generation_models.py
python ml/rag/scripts/export_projector.py
```

Export de TensorFlow Projector:

```text
ml/rag/results/projector/vectors.tsv
ml/rag/results/projector/metadata.tsv
ml/rag/results/projector/pca_preview.png
```

## Verificació

Tests Android:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Backend:

```powershell
python -m py_compile ml/rag/scripts/rag_backend.py
```

Validació manual ja feta en mòbil físic:

- `/health` retorna `{"status":"ok"}`;
- `/rag/demo-session-summary` retorna resposta amb `qwen3-coder-next`;
- `/rag/demo-guided-question?question_id=confidence_meaning` retorna una FAQ RAG;
- el selector de Perfil mostra els tres modes;
- el mode Backend RAG genera resum i fonts;
- els chunks es poden obrir individualment.

## Captures recomanades per a l'entrega

Les captures actuals són suficients per documentar la integració. Per completar la nova funcionalitat, és recomanable afegir-ne una més:

- detall de sessió amb la secció `Preguntes guiades RAG`;
- diàleg d'una pregunta guiada amb resposta i fonts.

## Estructura rellevant

```text
app/
  src/main/java/com/udl/smartrain/
    ml/                       Generadors RAG i TFLite
    ui/screens/               Pantalles Compose
    ui/viewmodel/             Flux app, sessions i preferències
ml/
  models/                     Model TensorFlow Lite
  rag/
    data/                     Corpus RAG
    chroma_db/smartrain/      Índex ChromaDB
    scripts/                  Construcció, avaluació i backend
    results/                  Resultats i export Projector
```
