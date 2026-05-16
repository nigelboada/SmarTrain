# SmarTrain - Informe tecnic del projecte

Data de consolidacio: 2026-05-14

Actualitzacio RAG: 2026-05-16

Validacio nova:

- TensorFlow Projector carregat manualment amb 42 vectors i 768 dimensions.
- Visualitzacions comprovades: PCA, t-SNE i UMAP.
- Color by validat amb `category` i `source`.
- Index ChromaDB validat a `ml/rag/chroma_db/smartrain`.
- Comparativa d'embeddings executada:
  - `nomic-embed-text`: Hit@6 0,833, MRR 0,708.
  - `sentence-transformers/all-MiniLM-L6-v2`: Hit@6 0,833, MRR 0,694.
  - Decisio: mantenir `nomic-embed-text`.
- Backend FastAPI validat:
  - `POST /rag/retrieve`: 200 OK amb fonts recuperades de Chroma.
  - `POST /rag/session-summary`: 200 OK amb Ollama local `gemma3:1b`.
- Ollama Cloud amb `qwen3-coder-next` queda pendent de validacio manual per error DNS resolent `ollama.com` en aquest entorn.

## 1. Resum executiu

SmarTrain es una app Android per registrar sessions esportives i enriquir-les amb:

- classificacio d'activitat local amb TensorFlow Lite;
- resum post-sessio basat en RAG;
- generacio opcional amb Ollama Cloud o Ollama local;
- fallback local per regles quan la IA no respon.

Decisions finals:

| Component | Decisio |
| --- | --- |
| Model ML embarcat | `cnn_deep` exportat a TensorFlow Lite |
| Recuperador RAG | TF-IDF cosine |
| Generador IA principal | `qwen3-coder-next` via Ollama Cloud |
| Fallback | Generador local per regles |

La integracio IA esta funcional per a prototip/demo. El punt pendent mes important es fer una validacio final en dispositiu real amb `qwen3-coder-next` configurat a l'app i una prova explicita de fallback.

## 2. Arquitectura

```text
SmarTrain/
  app/
    src/main/java/com/udl/smartrain/
      data/local/          Room, sensors i localitzacio
      data/repository/     sincronitzacio Room/Firestore
      domain/model/        Session
      ml/                  TFLite, RAG local i Ollama
      service/             TrackingService
      ui/                  pantalles Jetpack Compose
    src/main/assets/
      model_v1.tflite
  ml/
    data/                  dataset UCI HAR processat i cru
    models/                models entrenats/exportats
    rag/                   corpus, avaluacio i scripts RAG
    results/               resultats ML
    scripts/               preprocessament i entrenament
  docs/
    PROJECT_REPORT.md
    RAG_IMPLEMENTATION_GUIDE.md
```

Flux ML dins l'app:

```text
SensorProvider
  -> TrackingService
  -> finestra de 128 mostres
  -> preprocessament Android
  -> ActivityClassifier TFLite
  -> ActivityRecognitionState
  -> resum de sessio
  -> Room / Firestore
  -> RAG post-sessio
```

Flux RAG/IA actualitzat:

```text
Session
  -> backend RAG remot opcional (/rag/session-summary)
  -> ChromaDB/Pinecone al backend + chunks + scores
  -> qwen3-coder-next via Ollama Cloud
  -> OllamaRagGenerator (/api/chat) si esta activat com a mode directe
  -> fallback RuleBasedRagAnswerGenerator si falla
  -> persistencia de resposta, proveidor, model, latencia, chunks font i motiu de fallback
```

La guia operativa del nou pipeline RAG es troba a `docs/RAG_IMPLEMENTATION_GUIDE.md`.

## 3. Experimentacio ML

### Problema

L'app necessita classificar activitat humana a partir de l'accelerometre del mobil per resumir la intensitat de la sessio.

Categories finals de producte:

| Classe UCI HAR | Categoria SmarTrain |
| --- | --- |
| Caminar | Desplacament suau |
| Pujar escales | Alta intensitat |
| Baixar escales | Alta intensitat |
| Seure | Repos |
| Dret | Repos |
| Estirat | Repos |

Limitacio principal: UCI HAR no conte accions especifiques de futbol com sprint, canvi de direccio, pressio o conduccio de pilota. El model valida el flux tecnic ML end-to-end, pero no s'ha de presentar com a classificador final de rendiment futbolistic.

### Dataset

Dataset utilitzat: UCI Human Activity Recognition using Smartphones.

| Element | Valor |
| --- | --- |
| Mostres totals | 10.299 |
| Entrenament original | 7.352 |
| Test original | 2.947 |
| Frequencia | 50 Hz |
| Finestra | 128 mostres, aprox. 2,56 s |
| Entrada Android | acceleracio total en 3 eixos |

Fitxers principals:

- `ml/scripts/preprocess.py`
- `ml/data/processed/X_train.npy`
- `ml/data/processed/y_train.npy`

### Models avaluats

| Model | Objectiu | Resultat |
| --- | --- | --- |
| Random Forest | Baseline classic | Accuracy 92,06% |
| CNN inicial | Primera CNN 1D | Validation accuracy 86,13% |
| `cnn_lite` | Model petit per mobil | F1 94,84% |
| `cnn_deep` | Millor capacitat | F1 96,02% |
| `cnn_separable` | Reduir parametres | F1 91,35% |

### Model ML seleccionat

Model seleccionat: `cnn_deep`.

| Metrica | Valor |
| --- | ---: |
| Accuracy test | 96,01% |
| F1 weighted | 96,02% |
| Mida TFLite | 55.208 bytes |
| Inferencia mitjana TFLite | 0,078 ms |
| Mostres de test | 1.103 |

Justificacio:

- millor rendiment global;
- mida molt baixa per a Android;
- inferencia rapida;
- exportacio TFLite validada;
- mateix hash SHA-256 a `ml/models/model_v1.tflite` i `app/src/main/assets/model_v1.tflite`.

Hash validat:

```text
3A0176C01ACE86F258E87B9B60763C44A2216AC70A04F0A3D685210577BC00B8
```

## 4. RAG local

### Objectiu

El RAG interpreta els resultats ML i genera una recomanacio post-sessio basada en documents locals. No substitueix el classificador: el complementa.

Corpus:

- `ml/rag/data/knowledge_base.jsonl`
- 12 fragments documentals;
- categories: recomanacio d'entrenament, interpretacio del model, experimentacio ML i disseny RAG.

### Avaluacio de recuperadors

Script:

```powershell
python ml/rag/scripts/evaluate_rag.py
```

Resultats:

| Recuperador | Top K | Hit@3 | MRR | Decisio |
| --- | ---: | ---: | ---: | --- |
| `keyword_overlap` | 3 | 100% | 0,83 | Baseline |
| `tfidf_cosine` | 3 | 100% | 1,00 | Seleccionat |

Decisio: es selecciona `tfidf_cosine` perque recupera el document esperat en primera posicio en totes les preguntes d'avaluacio.

## 5. Comparativa de models generatius

### Objectiu

Comparar models locals i cloud per generar el resum RAG final. El retrieval es mante constant amb TF-IDF; per tant, la comparacio mesura sobretot la qualitat de generacio.

Script principal:

```powershell
python ml/rag/scripts/compare_generation_models.py --timeout 180
```

Fitxers generats:

- `ml/rag/results/generation_model_comparison.json`
- `ml/rag/results/generation_model_comparison.csv`

### Tasques d'avaluacio

S'han executat 10 tasques per model:

- 6 preguntes documentals;
- 4 sessions sintetiques.

### Metriques

Metriques quantitatives:

- latencia mitjana;
- p95 de latencia;
- errors/timeouts;
- `grounded_overlap`;
- cobertura de termes esperats.

Metriques qualitatives automatitzades:

- qualitat linguistica en catala;
- respecte del context RAG;
- risc d'inventar dades;
- utilitat de la recomanacio.

Les puntuacions qualitatives van d'1 a 5. Son proxies automatics orientatius i no una auditoria humana formal, pero permeten comparar models sense omplir un CSV manual.

### Models comparats

| Proveidor | Model | Tasques OK | Errors/timeouts | Latencia mitjana | P95 latencia | Catala | Respecte RAG | No invencio | Utilitat | Grounded overlap |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| cloud | `gemma3:4b` | 10/10 | 0 | 702,98 ms | 977,94 ms | 2,516 | 4,200 | 5,000 | 3,301 | 0,733 |
| local | `gemma4:latest` | 10/10 | 0 | 43.190,86 ms | 96.556,10 ms | 3,003 | 3,982 | 5,000 | 3,873 | 0,661 |
| cloud | `qwen3-coder-next` | 10/10 | 0 | 3.378,32 ms | 12.119,02 ms | 3,330 | 3,882 | 4,940 | 3,735 | 0,627 |
| local | `gemma3:1b` | 10/10 | 0 | 8.022,97 ms | 11.958,88 ms | 2,586 | 3,597 | 4,840 | 3,430 | 0,532 |
| local | `qwen3.6:latest` | 9/10 | 1 | 63.571,25 ms | 119.281,17 ms | 3,070 | 3,710 | 5,000 | 3,810 | 0,570 |
| cloud | `gpt-oss:20b` | 8/10 | 2 | 1.554,34 ms | 2.191,71 ms | 2,781 | 3,596 | 4,825 | 3,599 | 0,532 |

### Exemples de resposta

`gemma3:4b`, tasca `q_001`:

> Si la sessio te molta alta intensitat, es recomana alternar blocs d'alta intensitat amb recuperacions suficients.

`gemma4:latest`, tasca `q_001`:

> Si la sessio te alta intensitat, pot contenir canvis de ritme, acceleracions o moviments exigents. Es recomana alternar aquests blocs amb recuperacions suficients. Recorda fer recuperacio, hidratacio i analitzar la carrega despres de sessio.

`qwen3-coder-next`, tasca `q_001`:

> Indica que la sessio inclou molts canvis de ritme, acceleracions o moviments exigents, com pujar o baixar escales. Es recomana alternar aquests blocs amb recuperacions suficients i, si es la segona sessio intensa seguida, la propera pot prioritzar tecnica, mobilitat o treball aerobi suau.

`gemma3:1b`, tasca `q_001`:

> Si la sessio te molta intensitat, es possible que s'utilitzin aproximacions tecniques per a accions que requereixen un moviment o un ritme intens. Recomanem alternar aquests blocs amb recuperacions suficients per evitar canvis de ritme.

`qwen3.6:latest`, tasca `s_004`:

> La sessio s'ha caracteritzat per alta intensitat amb una confianca moderada (48%). Ates que 7 de 16 blocs son d'alta intensitat, es recomana prioritzar la recuperacio, la hidratacio i l'analisi de la carrega. Per a la propera sessio, es suggereix treballar la tecnica, la mobilitat o un treball aerobi suau per equilibrar l'estres acumulat.

`gpt-oss:20b`, tasca `q_001`:

> Una sessio amb molta alta intensitat indica que la major part de l'entrenament es va dedicar a blocs exigents, com pujar o baixar escales, acceleracions o moviments intensos. Recomanacio accionable: inclou recuperacio activa, hidratacio i analisi de la carrega.

### Decisio final

Model seleccionat: `qwen3-coder-next` via Ollama Cloud.

Justificacio:

- completa 10/10 tasques sense errors;
- latencia mitjana acceptable per a una pantalla de detall;
- millor utilitat de recomanacio que `gemma3:4b`, que es molt rapid pero massa breu;
- evita les latencies no viables de `gemma4:latest` i `qwen3.6:latest`;
- manté bon respecte del context RAG;
- te puntuacio alta en no invencio de dades.

Models descartats:

| Model | Motiu |
| --- | --- |
| `gemma3:4b` | Molt rapid, pero respostes massa breus i menys utils |
| `gpt-oss:20b` | 2 respostes buides en 10 tasques |
| `gemma3:1b` | Viable localment, pero menys qualitat i latencia alta |
| `gemma4:latest` | Qualitat correcta, pero latencia mitjana de 43 s |
| `qwen3.6:latest` | Timeout i latencia mitjana de 63 s |

## 6. Integracio IA a Android

### Implementat

| Element | Estat |
| --- | --- |
| RAG local per regles | Fet |
| Recuperacio de documents locals | Fet |
| Generador Ollama `/api/chat` | Fet |
| Compatibilitat Ollama local/ngrok/cloud | Fet |
| API key opcional en memoria | Fet |
| Fallback local automatic | Fet |
| Persistencia de resposta RAG | Fet |
| Persistencia de proveidor/model/latencia | Fet |
| Visualitzacio en detall de sessio | Fet |
| Missatge de fallback | Fet |
| Tests unitaris locals existents | Fet |

Fitxers principals:

- `app/src/main/java/com/udl/smartrain/ml/SessionRagRecommender.kt`
- `app/src/main/java/com/udl/smartrain/ml/OllamaRagGenerator.kt`
- `app/src/main/java/com/udl/smartrain/ml/RagGenerationSettings.kt`
- `app/src/main/java/com/udl/smartrain/ui/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/udl/smartrain/ui/screens/SessionDetailScreen.kt`

### Configuracio recomanada per demo

En la pantalla de perfil:

| Camp | Valor |
| --- | --- |
| Usar Ollama | Activat |
| Base URL | `https://ollama.com` |
| Model | `qwen3-coder-next` |
| API key | clau valida d'Ollama Cloud |

### Validacio necessaria abans d'entrega

1. Crear una sessio real al dispositiu.
2. Guardar-la amb Ollama Cloud activat.
3. Obrir el detall de sessio.
4. Confirmar que mostra `ollama:qwen3-coder-next`.
5. Confirmar que hi ha latencia persistida.
6. Confirmar que la resposta esta en catala i usa fonts recuperades.
7. Repetir amb API key buida o endpoint incorrecte.
8. Confirmar que es guarda fallback `rules:rule_based`.
9. Confirmar que es mostra el motiu del fallback.

### Punts pendents o millores recomanades

No bloquegen la demo, pero millorarien la integracio:

| Prioritat | Tasca | Motiu |
| --- | --- | --- |
| Alta | Regenerar la API key usada durant proves | La clau s'ha compartit al xat i s'ha de considerar exposada |
| Alta | Validar en dispositiu real el flux `qwen3-coder-next` | L'experiment Python esta complet, falta prova final in-app |
| Mitjana | Persistir configuracio IA de perfil | Ara `DebugRagGenerationSettings` viu en memoria; si l'app es tanca, es perd |
| Mitjana | Afegir test unitari per error Ollama i fallback | Ja hi ha fallback al codi, pero convé fixar-lo amb test |
| Mitjana | Evitar guardar API key en sessions o logs | Actualment no es persisteix, cal mantenir aquesta regla |
| Baixa | Ampliar corpus RAG | Milloraria qualitat i cobertura de recomanacions |
| Baixa | Avaluar embeddings semantics | Pot millorar recuperacio respecte TF-IDF |

Conclusio d'integracio: no falta cap bloc estructural per integrar el model IA al prototip. Falta sobretot validacio final en app real, rotacio de credencials i, si es vol pujar qualitat, persistir settings i ampliar tests.

## 7. Reproduccio

Instal.lar dependencies Python:

```powershell
pip install -r ml/requirements.txt
```

Preprocessament ML:

```powershell
python ml/scripts/preprocess.py
```

Entrenament i comparacio ML:

```powershell
python ml/scripts/train_baseline.py
python ml/scripts/train_cnn.py
python ml/scripts/train_model_comparison.py
```

Avaluacio RAG:

```powershell
python ml/rag/scripts/evaluate_rag.py
```

Comparativa generativa local/cloud:

```powershell
$env:OLLAMA_API_KEY="..."
python ml/rag/scripts/compare_generation_models.py --timeout 180
```

Tests Android:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## 8. Limitacions

- El model ML esta entrenat amb UCI HAR, no amb dades reals de futbol.
- Les categories d'alta intensitat son una aproximacio basada en pujar/baixar escales.
- La posicio del mobil pot afectar les prediccions.
- El corpus RAG encara es petit.
- TF-IDF es transparent i reproduible, pero no semantic.
- `qwen3-coder-next` depen de connexio cloud.
- Les recomanacions son orientatives i no substitueixen criteri professional d'entrenament.
