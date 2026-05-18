# SmarTrain: experimentaciÃƒÆ’Ã‚Â³ ML i RAG

Aquest document resumeix el procÃƒÆ’Ã‚Â©s d'entrenament, selecciÃƒÆ’Ã‚Â³, exportaciÃƒÆ’Ã‚Â³ i integraciÃƒÆ’Ã‚Â³ del model ML de SmarTrain, i tambÃƒÆ’Ã‚Â© la implementaciÃƒÆ’Ã‚Â³ del RAG propi utilitzat a l'aplicaciÃƒÆ’Ã‚Â³.

## 1. DescripciÃƒÆ’Ã‚Â³ del problema

SmarTrain vol convertir dades de moviment capturades pel mÃƒÆ’Ã‚Â²bil en informaciÃƒÆ’Ã‚Â³ ÃƒÆ’Ã‚Âºtil per a una sessiÃƒÆ’Ã‚Â³ esportiva. El problema tÃƒÆ’Ã‚Â© dues parts:

- classificar finestres de sensor amb un model ML lleuger;
- explicar el resultat a l'usuari amb llenguatge natural i fonts de context.

El model ML dona prediccions i confianÃƒÆ’Ã‚Â§a. El RAG aporta interpretaciÃƒÆ’Ã‚Â³, prudÃƒÆ’Ã‚Â¨ncia i traÃƒÆ’Ã‚Â§abilitat.

## 2. Dataset utilitzat

El model d'activitat s'ha entrenat amb UCI HAR. Aquest dataset contÃƒÆ’Ã‚Â© lectures d'accelerÃƒÆ’Ã‚Â²metre i giroscopi amb etiquetes d'activitat humana.

LimitaciÃƒÆ’Ã‚Â³ important: UCI HAR no ÃƒÆ’Ã‚Â©s un dataset especÃƒÆ’Ã‚Â­fic de futbol. Per aixÃƒÆ’Ã‚Â² SmarTrain interpreta algunes classes com a aproximacions tÃƒÆ’Ã‚Â¨cniques:

- pujar o baixar escales com a indicador d'alta intensitat;
- caminar com a desplaÃƒÆ’Ã‚Â§ament suau;
- estar quiet com a repÃƒÆ’Ã‚Â²s.

Aquesta limitaciÃƒÆ’Ã‚Â³ es mostra a l'app mitjanÃƒÆ’Ã‚Â§ant el resum RAG.

## 3. Preprocessament

El preprocessament converteix les lectures en finestres compatibles amb el model:

- cÃƒÆ’Ã‚Â rrega de dades;
- normalitzaciÃƒÆ’Ã‚Â³;
- separaciÃƒÆ’Ã‚Â³ d'entrenament i validaciÃƒÆ’Ã‚Â³;
- construcciÃƒÆ’Ã‚Â³ de tensors;
- generaciÃƒÆ’Ã‚Â³ de metadades per a entrenament i exportaciÃƒÆ’Ã‚Â³.

Script principal:

```text
ml/scripts/preprocess.py
```

## 4. Models avaluats

Durant l'experimentaciÃƒÆ’Ã‚Â³ s'han comparat diversos enfocaments de classificaciÃƒÆ’Ã‚Â³ de sÃƒÆ’Ã‚Â¨ries temporals:

- models densos sobre caracterÃƒÆ’Ã‚Â­stiques agregades;
- xarxes convolucionals lleugeres;
- variants amb diferent mida de finestra;
- variants amb diferent nombre d'unitats i capes.

Els criteris de selecciÃƒÆ’Ã‚Â³ han estat:

- exactitud i estabilitat;
- mida del model;
- latÃƒÆ’Ã‚Â¨ncia al mÃƒÆ’Ã‚Â²bil;
- facilitat d'exportaciÃƒÆ’Ã‚Â³ a TensorFlow Lite.

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

## 6. Model final i exportaciÃƒÆ’Ã‚Â³

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

## 7. IntegraciÃƒÆ’Ã‚Â³ a l'app

L'app captura dades del dispositiu, executa el model i guarda les sessions.

Flux end-to-end:

```text
mÃƒÆ’Ã‚Â²bil
  -> sensors
  -> model TensorFlow Lite
  -> resum ML de sessiÃƒÆ’Ã‚Â³
  -> generador local, qwen3-coder-next o Backend RAG
  -> detall de sessiÃƒÆ’Ã‚Â³
```

La interfÃƒÆ’Ã‚Â­cie mostra:

- durada;
- distÃƒÆ’Ã‚Â ncia;
- activitat dominant;
- confianÃƒÆ’Ã‚Â§a mitjana;
- nombre de prediccions;
- blocs d'alta intensitat;
- lÃƒÆ’Ã‚Â­nia temporal d'activitat;
- interpretaciÃƒÆ’Ã‚Â³ post-sessiÃƒÆ’Ã‚Â³.

## 8. RAG per a l'aplicaciÃƒÆ’Ã‚Â³

### Problema

El classificador dona una etiqueta, perÃƒÆ’Ã‚Â² un usuari no expert necessita una explicaciÃƒÆ’Ã‚Â³. El RAG permet transformar la sortida del model en una recomanaciÃƒÆ’Ã‚Â³ contextualitzada i verificable.

### Dades utilitzades

El corpus RAG combina:

- base de coneixement prÃƒÆ’Ã‚Â²pia sobre interpretaciÃƒÆ’Ã‚Â³ del model;
- recomanacions d'entrenament prudents;
- documents FAQ per respondre preguntes guiades;
- documents sobre qualitat del sensor, posicio del mobil i estabilitat del mostreig;
- resultats i informes d'experimentaciÃƒÆ’Ã‚Â³;
- sessions anonimitzades de prova.

### Estructura

```text
ml/rag/
  data/                     Corpus en JSONL
  eval/                     Preguntes i sessions de prova
  chroma_db/smartrain/      ÃƒÆ’Ã‚Ândex vectorial
  scripts/                  ConstrucciÃƒÆ’Ã‚Â³, comparaciÃƒÆ’Ã‚Â³ i backend
  results/                  Resultats d'avaluaciÃƒÆ’Ã‚Â³
```

### Tecnologia

- FastAPI per exposar el backend.
- ChromaDB com a vector store.
- `nomic-embed-text` per generar embeddings locals.
- Ollama Cloud amb `qwen3-coder-next` per generar respostes.
- TensorFlow Projector per visualitzar embeddings.

## 9. ExperimentaciÃƒÆ’Ã‚Â³ RAG

S'han validat tres parts:

- recuperaciÃƒÆ’Ã‚Â³ de fragments rellevants;
- generaciÃƒÆ’Ã‚Â³ amb diferents models;
- integraciÃƒÆ’Ã‚Â³ completa amb l'app Android.

Resultat de l'ÃƒÆ’Ã‚Â­ndex Chroma:

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

La visualitzaciÃƒÆ’Ã‚Â³ amb PCA, t-SNE i UMAP mostra agrupacions per `category` i `source`, cosa que ajuda a comprovar que els documents no estan barrejats aleatÃƒÆ’Ã‚Â²riament.

## 10. ComparaciÃƒÆ’Ã‚Â³ de models amb RAG

S'han comparat respostes generades amb diferents opcions locals i cloud. El criteri principal no ha estat nomÃƒÆ’Ã‚Â©s la fluÃƒÆ’Ã‚Â¯desa, sinÃƒÆ’Ã‚Â³:

- respecte pel context recuperat;
- no invenciÃƒÆ’Ã‚Â³ de mÃƒÆ’Ã‚Â¨triques;
- resposta en catalÃƒÆ’Ã‚Â ;
- utilitat per a l'usuari;
- latÃƒÆ’Ã‚Â¨ncia acceptable;
- capacitat de seguir una resposta prudent.

El model seleccionat per al mode cloud i el backend ÃƒÆ’Ã‚Â©s:

```text
qwen3-coder-next
```

El mode local per regles es mantÃƒÆ’Ã‚Â© com a fallback perquÃƒÆ’Ã‚Â¨ l'app continuÃƒÆ’Ã‚Â¯ funcionant si falla la xarxa, el backend o la API.

## 11. Funcionalitats RAG prÃƒÆ’Ã‚Â²pies

El mode `Backend RAG` aporta funcionalitats que el mode directe de model no pot oferir sol:

- fonts recuperades;
- chunks visibles;
- scores de similitud;
- fragments clicables i ampliables;
- FAQ RAG amb preguntes guiades post-sessiÃƒÆ’Ã‚Â³.

Les preguntes guiades implementades sÃƒÆ’Ã‚Â³n:

- com millorar la propera sessiÃƒÆ’Ã‚Â³;
- per quÃƒÆ’Ã‚Â¨ es recomana una acciÃƒÆ’Ã‚Â³;
- quines limitacions tÃƒÆ’Ã‚Â© la predicciÃƒÆ’Ã‚Â³;
- quina recuperaciÃƒÆ’Ã‚Â³ convÃƒÆ’Ã‚Â©;
- quÃƒÆ’Ã‚Â¨ significa la confianÃƒÆ’Ã‚Â§a del model;
- com interpretar els blocs d'alta intensitat.

Aquest enfocament evita un chatbot genÃƒÆ’Ã‚Â¨ric i dona una utilitat concreta al RAG dins el flux principal de l'app.
Les FAQ poden funcionar encara que el resum principal s'hagi generat amb mode local o cloud, sempre que el backend RAG estigui actiu i accessible.

## 12. Resultats experimentals

El sistema complet funciona end-to-end:

- el model TFLite s'executa dins l'app;
- la sessiÃƒÆ’Ã‚Â³ guarda mÃƒÆ’Ã‚Â¨triques i prediccions;
- el selector de Perfil permet triar el mode IA;
- el backend RAG recupera chunks amb ChromaDB;
- `qwen3-coder-next` genera la interpretaciÃƒÆ’Ã‚Â³;
- l'app mostra resposta, fonts i latÃƒÆ’Ã‚Â¨ncia.

Validacions manuals en mÃƒÆ’Ã‚Â²bil fÃƒÆ’Ã‚Â­sic:

- `http://192.168.1.14:8000/health` retorna `{"status":"ok"}`;
- `http://192.168.1.14:8000/rag/demo-session-summary` retorna resposta RAG;
- el mode Backend RAG funciona des de l'app;
- les fitxes de chunks es poden obrir individualment.

## 13. DiscussiÃƒÆ’Ã‚Â³

El sistema ÃƒÆ’Ã‚Â©s ÃƒÆ’Ã‚Âºtil perquÃƒÆ’Ã‚Â¨ separa clarament tres nivells:

- ML: predicciÃƒÆ’Ã‚Â³ objectiva de finestres de moviment;
- RAG: recuperaciÃƒÆ’Ã‚Â³ de coneixement controlat;
- IA generativa: explicaciÃƒÆ’Ã‚Â³ final per a l'usuari.

La limitaciÃƒÆ’Ã‚Â³ principal ÃƒÆ’Ã‚Â©s que el dataset no ÃƒÆ’Ã‚Â©s especÃƒÆ’Ã‚Â­fic de futbol. Per aixÃƒÆ’Ã‚Â² les recomanacions es formulen com a aproximacions i no com a diagnÃƒÆ’Ã‚Â²stics esportius definitius.

## 14. Conclusions

SmarTrain compleix el flux complet demanat:

- entrenament i exportaciÃƒÆ’Ã‚Â³ d'un model ML;
- integraciÃƒÆ’Ã‚Â³ del model a l'app;
- interfÃƒÆ’Ã‚Â­cie per mostrar resultats;
- backend integrat;
- RAG propi funcional;
- documentaciÃƒÆ’Ã‚Â³ reproduÃƒÆ’Ã‚Â¯ble.

La funcionalitat RAG final no ÃƒÆ’Ã‚Â©s nomÃƒÆ’Ã‚Â©s un resum: permet veure fonts i fer preguntes guiades sobre la sessiÃƒÆ’Ã‚Â³, cosa que dona valor real a la integraciÃƒÆ’Ã‚Â³.

## 15. ReproducciÃƒÆ’Ã‚Â³

Entrenament i exportaciÃƒÆ’Ã‚Â³ ML:

```powershell
python ml/scripts/preprocess.py
python ml/scripts/train_model.py
python ml/scripts/convert_to_tflite.py
```

ConstrucciÃƒÆ’Ã‚Â³ RAG:

```powershell
python ml/rag/scripts/build_knowledge_base.py
python ml/rag/scripts/build_vector_index.py
```

Backend:

```powershell
uvicorn ml.rag.scripts.rag_backend:app --host 0.0.0.0 --port 8000
```

AvaluaciÃƒÆ’Ã‚Â³ RAG:

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

