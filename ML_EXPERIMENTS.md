# SmarTrain: experimentació ML i RAG

Aquest document resumeix el procés d'entrenament, selecció, exportació i integració del model ML de SmarTrain, i també la implementació del RAG propi utilitzat a l'aplicació.

## 1. Descripció del problema

SmarTrain vol convertir dades de moviment capturades pel mòbil en informació útil per a una sessió esportiva. El problema té dues parts:

- classificar finestres de sensor amb un model ML lleuger;
- explicar el resultat a l'usuari amb llenguatge natural i fonts de context.

El model ML dona prediccions i confiança. El RAG aporta interpretació, prudència i traçabilitat.

## 2. Dataset utilitzat

El model d'activitat s'ha entrenat amb UCI HAR. Aquest dataset conté lectures d'acceleròmetre i giroscopi amb etiquetes d'activitat humana.

Limitació important: UCI HAR no és un dataset específic de futbol. Per això SmarTrain interpreta algunes classes com a aproximacions tècniques:

- pujar o baixar escales com a indicador d'alta intensitat;
- caminar com a desplaçament suau;
- estar quiet com a repòs.

Aquesta limitació es mostra a l'app mitjançant el resum RAG.

## 3. Preprocessament

El preprocessament converteix les lectures en finestres compatibles amb el model:

- càrrega de dades;
- normalització;
- separació d'entrenament i validació;
- construcció de tensors;
- generació de metadades per a entrenament i exportació.

Script principal:

```text
ml/scripts/preprocess.py
```

## 4. Models avaluats

Durant l'experimentació s'han comparat diversos enfocaments de classificació de sèries temporals:

- models densos sobre característiques agregades;
- xarxes convolucionals lleugeres;
- variants amb diferent mida de finestra;
- variants amb diferent nombre d'unitats i capes.

Els criteris de selecció han estat:

- exactitud i estabilitat;
- mida del model;
- latència al mòbil;
- facilitat d'exportació a TensorFlow Lite.

## 5. Ajust d'hiperparametres

S'ha afegit un experiment especific i reproduible d'ajust d'hiperparametres:

```powershell
py -3.12 ml/scripts/tune_hyperparameters.py
```

El script genera:

```text
ml/results/hyperparameter_tuning.json
ml/results/hyperparameter_tuning.csv
ml/models/model_tuned_candidate.tflite
```

Despres de validar el candidat ajustat, s'ha copiat tambe com a model final de l'app:

```text
ml/models/model_v1.tflite
app/src/main/assets/model_v1.tflite
```

Els hiperparametres explorats han estat:

- mida de batch;
- nombre d'epoques amb early stopping;
- taxa d'aprenentatge;
- nombre de filtres convolucionals;
- unitats de la capa densa;
- dropout.

La metrica de seleccio ha estat `val_weighted_f1`, amb desempat per `val_loss` i temps d'entrenament. S'han provat sis configuracions CNN 1D.

Millor configuracio:

```text
name: medium_lr_5e4_do35_b64
filters_1: 48
filters_2: 96
dense_units: 80
dropout: 0,35
learning_rate: 0,0005
batch_size: 64
epochs_ran: 18
val_weighted_f1: 0,9755
test_weighted_f1: 0,9637
TFLite: 38.528 bytes
inferencia TFLite: 0,058 ms
```

Aquest resultat documenta l'ajust d'hiperparametres demanat i mostra que una configuracio mitjana, no la mes gran, ofereix el millor equilibri entre qualitat, mida i latencia.

## 6. Model final i exportació

El model final s'exporta a TensorFlow Lite per executar-se dins l'app Android.

Fitxers rellevants:

```text
ml/models/
app/src/main/assets/
```

Flux:

```text
dades sensor -> preprocessament -> entrenament -> export TFLite -> app Android
```

## 7. Integració a l'app

L'app captura dades del dispositiu, executa el model i guarda les sessions.

Flux end-to-end:

```text
mòbil
  -> sensors
  -> model TensorFlow Lite
  -> resum ML de sessió
  -> generador local, qwen3-coder-next o Backend RAG
  -> detall de sessió
```

La interfície mostra:

- durada;
- distància;
- activitat dominant;
- confiança mitjana;
- nombre de prediccions;
- blocs d'alta intensitat;
- línia temporal d'activitat;
- interpretació post-sessió.

## 8. RAG per a l'aplicació

### Problema

El classificador dona una etiqueta, però un usuari no expert necessita una explicació. El RAG permet transformar la sortida del model en una recomanació contextualitzada i verificable.

### Dades utilitzades

El corpus RAG combina:

- base de coneixement pròpia sobre interpretació del model;
- recomanacions d'entrenament prudents;
- documents FAQ per respondre preguntes guiades;
- documents sobre qualitat del sensor, posicio del mobil i estabilitat del mostreig;
- resultats i informes d'experimentació;
- sessions anonimitzades de prova.

### Estructura

```text
ml/rag/
  data/                     Corpus en JSONL
  eval/                     Preguntes i sessions de prova
  chroma_db/smartrain/      Índex vectorial
  scripts/                  Construcció, comparació i backend
  results/                  Resultats d'avaluació
```

### Tecnologia

- FastAPI per exposar el backend.
- ChromaDB com a vector store.
- `nomic-embed-text` per generar embeddings locals.
- Ollama Cloud amb `qwen3-coder-next` per generar respostes.
- TensorFlow Projector per visualitzar embeddings.

## 9. Experimentació RAG

S'han validat tres parts:

- recuperació de fragments rellevants;
- generació amb diferents models;
- integració completa amb l'app Android.

Resultat de l'índex Chroma:

```text
raw_documents: 33
chunks: 33
embedding_dimension: 768
```

Export TensorFlow Projector:

```text
ml/rag/results/projector/vectors.tsv
ml/rag/results/projector/metadata.tsv
ml/rag/results/projector/pca_preview.png
```

La visualització amb PCA, t-SNE i UMAP mostra agrupacions per `category` i `source`, cosa que ajuda a comprovar que els documents no estan barrejats aleatòriament.

## 10. Comparació de models amb RAG

S'han comparat respostes generades amb diferents opcions locals i cloud. El criteri principal no ha estat només la fluïdesa, sinó:

- respecte pel context recuperat;
- no invenció de mètriques;
- resposta en català;
- utilitat per a l'usuari;
- latència acceptable;
- capacitat de seguir una resposta prudent.

El model seleccionat per al mode cloud i el backend és:

```text
qwen3-coder-next
```

El mode local per regles es manté com a fallback perquè l'app continuï funcionant si falla la xarxa, el backend o la API.

## 11. Funcionalitats RAG pròpies

El mode `Backend RAG` aporta funcionalitats que el mode directe de model no pot oferir sol:

- fonts recuperades;
- chunks visibles;
- scores de similitud;
- fragments clicables i ampliables;
- FAQ RAG amb preguntes guiades post-sessió.

Les preguntes guiades implementades són:

- com millorar la propera sessió;
- per què es recomana una acció;
- quines limitacions té la predicció;
- quina recuperació convé;
- què significa la confiança del model;
- com interpretar els blocs d'alta intensitat.

Aquest enfocament evita un chatbot genèric i dona una utilitat concreta al RAG dins el flux principal de l'app.
Les FAQ poden funcionar encara que el resum principal s'hagi generat amb mode local o cloud, sempre que el backend RAG estigui actiu i accessible.

## 12. Resultats experimentals

El sistema complet funciona end-to-end:

- el model TFLite s'executa dins l'app;
- la sessió guarda mètriques i prediccions;
- el selector de Perfil permet triar el mode IA;
- el backend RAG recupera chunks amb ChromaDB;
- `qwen3-coder-next` genera la interpretació;
- l'app mostra resposta, fonts i latència.

Validacions manuals en mòbil físic:

- `http://192.168.1.14:8000/health` retorna `{"status":"ok"}`;
- `http://192.168.1.14:8000/rag/demo-session-summary` retorna resposta RAG;
- el mode Backend RAG funciona des de l'app;
- les fitxes de chunks es poden obrir individualment.

## 13. Discussió

El sistema és útil perquè separa clarament tres nivells:

- ML: predicció objectiva de finestres de moviment;
- RAG: recuperació de coneixement controlat;
- IA generativa: explicació final per a l'usuari.

La limitació principal és que el dataset no és específic de futbol. Per això les recomanacions es formulen com a aproximacions i no com a diagnòstics esportius definitius.

## 14. Conclusions

SmarTrain compleix el flux complet demanat:

- entrenament i exportació d'un model ML;
- integració del model a l'app;
- interfície per mostrar resultats;
- backend integrat;
- RAG propi funcional;
- documentació reproduïble.

La funcionalitat RAG final no és només un resum: permet veure fonts i fer preguntes guiades sobre la sessió, cosa que dona valor real a la integració.

## 15. Reproducció

Entrenament i exportació ML:

```powershell
python ml/scripts/preprocess.py
python ml/scripts/train_model.py
python ml/scripts/convert_to_tflite.py
```

Construcció RAG:

```powershell
python ml/rag/scripts/build_knowledge_base.py
python ml/rag/scripts/build_vector_index.py
```

Backend:

```powershell
uvicorn ml.rag.scripts.rag_backend:app --host 0.0.0.0 --port 8000
```

Avaluació RAG:

```powershell
python ml/rag/scripts/compare_embedding_models.py
python ml/rag/scripts/compare_generation_models.py
python ml/rag/scripts/export_projector.py
```

Tests Android:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## 16. Millora incremental del corpus RAG per a la demo

S'ha ampliat el corpus de `ml/rag/data/knowledge_base.jsonl` amb tres documents nous sobre qualitat de sensor:

- protocol curt abans de sensoritzar;
- posicio recomanada del dispositiu;
- diferencia entre millorar l'estabilitat del sensor i necessitar dades de futbol etiquetades.

La decisio d'afegir-los es justificada perque cobreixen preguntes reals de l'usuari i de la defensa del projecte: com fer una demo mes estable, per que el mobil pot introduir soroll i per que un millor sensor no substitueix un dataset especific de futbol.

S'han afegit tres preguntes d'avaluacio a `ml/rag/eval/questions.jsonl` i s'ha repetit l'experiment amb `python ml/rag/scripts/evaluate_rag.py`.

Resultat despres de l'ampliacio:

- documents del corpus: 29;
- preguntes d'avaluacio: 13;
- recuperador seleccionat: `tfidf_cosine`;
- `hit_rate`: 1,00;
- `MRR`: 0,9615.

Per tant, l'ampliacio es considera beneficiosa: augmenta la cobertura funcional del RAG sense degradar la recuperacio principal.

