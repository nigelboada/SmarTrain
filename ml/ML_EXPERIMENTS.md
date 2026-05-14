# Documentacio d'experimentacio ML - SmarTrain

## 1. Problema

SmarTrain necessita classificar activitat humana a partir de l'accelerometre del mobil per enriquir una sessio esportiva amb senyals d'intensitat. L'objectiu funcional de producte es descriu en tres nivells:

- Repos: recuperacio o activitat molt baixa.
- Desplacament suau: caminar o moviment continu moderat.
- Alta intensitat: canvis de ritme o accions explosives.

El model entrenat per a l'entrega final utilitza el dataset public UCI HAR. Aquest dataset no conte accions especifiques de futbol i te 6 classes, no 3. Per tant, el model integrat valida el flux ML end-to-end de l'app, pero encara no substitueix un classificador final entrenat amb dades reals de futbol.

Mapeig conceptual utilitzat:

| Classe UCI HAR | Interpretacio a SmarTrain |
| :--- | :--- |
| Caminar | Desplacament suau |
| Pujar escales | Alta intensitat aproximada |
| Baixar escales | Alta intensitat aproximada |
| Seure | Repos |
| Dret | Repos |
| Estirat | Repos |

## 2. Dataset

Dataset utilitzat: UCI Human Activity Recognition using Smartphones.

- Mostres totals: 10.299.
- Entrenament original: 7.352 mostres.
- Test original: 2.947 mostres.
- Frequencia: 50 Hz.
- Finestra: 128 mostres, aproximadament 2,56 segons.
- Sensors: accelerometre i giroscopi.
- Entrada usada al model Android: acceleracio total en 3 eixos.
- Classes: caminar, pujar escales, baixar escales, seure, dret, estirat.

Dades processades:

- `ml/data/processed/X_train.npy`
- `ml/data/processed/y_train.npy`

## 3. Preprocessament

Script principal: `ml/scripts/preprocess.py`.

El preprocessament carrega els fitxers `total_acc_x_train.txt`, `total_acc_y_train.txt` i `total_acc_z_train.txt`, els apila en tensors `(samples, 128, 3)` i ajusta les etiquetes per comencar a 0. Aquest format coincideix amb l'entrada del model TensorFlow Lite integrat a Android: `[1][128][3]`.

Punts importants:

- Les finestres mantenen l'ordre temporal.
- Les dades UCI ja venen normalitzades.
- La inferencia a Android usa finestres lliscants de 128 lectures d'accelerometre.
- L'app demana mostres a 50 Hz (`20.000 us`) i filtra les lectures amb el timestamp del sensor per evitar finestres massa rapides quan el dispositiu entrega esdeveniments per sobre de la frequencia objectiu.

## 4. Models Avaluats

### Model 1: Random Forest baseline

Script: `ml/scripts/train_baseline.py`.

Model classic sobre caracteristiques tabulars UCI HAR. Serveix com a referencia de precisio, pero no es el candidat final per a mobil per mida i portabilitat.

Resultats historics:

| Metrica | Valor |
| :--- | :--- |
| Accuracy | 92,06% |
| F1 weighted | 0,92 |
| Precision mitjana | 0,92 |

### Model 2: CNN 1D inicial

Script: `ml/scripts/train_cnn.py`.

Arquitectura:

`Conv1D(64, 3)` -> `MaxPooling1D(2)` -> `Flatten()` -> `Dense(64)` -> `Dropout(0.5)` -> `Dense(6, softmax)`

Resultats historics:

| Metrica | Valor |
| :--- | :--- |
| Accuracy train | 90,94% |
| Accuracy validation | 86,13% |
| Mida TFLite anterior | 1.041.524 bytes |

### Model 3: variants CNN

Script: `ml/scripts/train_model_comparison.py`.

Aquest script compara tres arquitectures CNN i selecciona automaticament la millor segons F1 weighted:

| Variant | Arquitectura | Objectiu |
| :--- | :--- | :--- |
| `cnn_lite` | Conv1D + GlobalAveragePooling | Model petit i estable per mobil |
| `cnn_deep` | Conv1D + BatchNorm + mes filtres | Millorar capacitat del model |
| `cnn_separable` | SeparableConv1D | Reduir parametres i cost d'inferencia |

Sortides generades:

- `ml/results/model_comparison.json`
- `ml/results/confusion_matrix_final.png`
- `ml/results/confusion_matrix_cnn_lite.png` (nom historic; correspon al model final d'aquesta execucio)
- `ml/models/model_v1.tflite`
- `app/src/main/assets/model_v1.tflite`

## 5. Hiperparametres

| Experiment | Epochs | Batch | Optimizer | Regularitzacio | Criteri |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Random Forest | N/A | N/A | N/A | `max_depth=10` | Accuracy/F1 |
| CNN inicial | 15 | 32 | Adam | Dropout 0,5 | Validation accuracy |
| CNN lite | fins a 30 | 32 | Adam | Dropout 0,3 + EarlyStopping | F1 weighted |
| CNN deep | fins a 30 | 32 | Adam | BatchNorm + Dropout 0,4 + EarlyStopping | F1 weighted |
| CNN separable | fins a 30 | 32 | Adam | Dropout 0,35 + EarlyStopping | F1 weighted |

## 6. Metriques

Metriques utilitzades:

- Accuracy.
- F1 weighted.
- Precision, recall i F1 per classe.
- Matriu de confusio.
- Temps d'entrenament.
- Mida del model exportat.
- Temps mitja d'inferencia TFLite.

El script `train_model_comparison.py` calcula les metriques amb un split estratificat 70/15/15 a partir de les dades processades. L'execucio s'ha fet a Google Colab amb TensorFlow.

## 7. Resultats de l'Execucio a Colab

Fitxer de resultats: `ml/results/model_comparison.json`.

| Model | Accuracy test | F1 weighted | Temps entrenament | Estat |
| :--- | ---: | ---: | ---: | :--- |
| `cnn_lite` | 94,83% | 94,84% | 50,83 s | Candidat mobil |
| `cnn_deep` | **96,01%** | **96,02%** | **41,92 s** | **Seleccionat** |
| `cnn_separable` | 91,39% | 91,35% | 52,91 s | Descartat |

Model seleccionat: `cnn_deep`.

| Metrica final | Valor |
| :--- | :--- |
| Accuracy test | 96,01% |
| F1 weighted | 96,02% |
| Mida TFLite | 55.208 bytes |
| Inferencia mitjana TFLite | 0,078 ms |
| Mostres de test | 1.103 |
| Matriu de confusio | `ml/results/confusion_matrix_final.png` |

La mida del model final baixa de 1.041.524 bytes a 55.208 bytes, una reduccio aproximada del 94,7%.

## 8. Matriu de Confusio

La matriu de confusio del model final mostra resultats molt bons en classes dinamiques i una confusio moderada entre `Seure` i `Dret`, que son classes posturals similars.

Matriu del model `cnn_deep`:

| Real \ Prediccio | Caminar | Pujar | Baixar | Seure | Dret | Estirat |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: |
| Caminar | 183 | 0 | 0 | 0 | 1 | 0 |
| Pujar escales | 0 | 161 | 0 | 0 | 0 | 0 |
| Baixar escales | 0 | 0 | 148 | 0 | 0 | 0 |
| Seure | 0 | 0 | 0 | 171 | 22 | 0 |
| Dret | 0 | 0 | 0 | 21 | 185 | 0 |
| Estirat | 0 | 0 | 0 | 0 | 0 | 211 |

Imatge exportada: `ml/results/confusion_matrix_final.png`.

## 9. Optimitzacio per Mobil

El model final s'ha exportat a TensorFlow Lite amb `tf.lite.Optimize.DEFAULT`. El fitxer exportat s'ha copiat a:

- `ml/models/model_v1.tflite`
- `app/src/main/assets/model_v1.tflite`

Els dos fitxers tenen el mateix hash SHA-256:

`3A0176C01ACE86F258E87B9B60763C44A2216AC70A04F0A3D685210577BC00B8`

A Android, el model es carrega amb `Interpreter`, rep finestres de 128 mostres i publica:

- activitat actual,
- confianca,
- historic recent,
- resum persistent dins la sessio.

### 9.1 Validacio en dispositiu real

La integracio s'ha provat en un dispositiu Android real dins del flux complet de l'aplicacio: login Firebase, inici de sessio esportiva, permisos, foreground service, lectura de sensors, inferencia ML, finalitzacio, persistencia local amb Room, historial i sincronitzacio amb Firestore.

Durant aquesta prova inicial es va observar que el model tendia a predir `Baixar escales` o `Pujar escales` encara que el mobil estigues quiet o caminant. Aquest comportament no indicava necessariament un error del sensor, sino una diferencia entre el domini d'entrenament i el domini real d'inferencia:

- Android entrega l'accelerometre en m/s2.
- UCI HAR esta representat aproximadament en unitats `g`.
- En UCI HAR la gravetat acostuma a quedar alineada amb un eix concret segons la posicio de captura.
- En l'app real, l'orientacio del mobil a la cintura o a la butxaca canvia els eixos del senyal.

Per reduir aquesta diferencia s'ha afegit preprocessament a l'app abans d'invocar TensorFlow Lite:

- conversio de m/s2 a `g`;
- normalitzacio basica de l'eix dominant de gravetat per aproximar el format UCI HAR;
- regla de repos quan la magnitud de l'acceleracio es gairebe constant.
- mapatge de les classes UCI HAR a categories finals de producte: `Repos`, `Desplacament suau` i `Alta intensitat`.
- throttling temporal a 50 Hz amb `SensorEvent.timestamp` per construir finestres de 128 mostres comparables a les del dataset.

Despres d'aquest ajust, la deteccio en repos i el comportament general del model en moviment son mes coherents. L'app mostra les categories SmarTrain a l'usuari i conserva la classe UCI original en el registre intern de prediccions per facilitar la depuracio. Tot i aixi, les prediccions continuen sent orientatives, perque el model final encara no ha estat entrenat amb dades reals de futbolistes ni amb totes les orientacions possibles del dispositiu.

## 10. Comparacio i Seleccio Final

| Model | Resultat | Mida/exportacio | Decisio |
| :--- | :--- | :--- | :--- |
| Random Forest | Accuracy 92,06% | No exportat a TFLite | Baseline |
| CNN inicial | Validation accuracy 86,13% | 1.041.524 bytes | Substituit |
| CNN lite | F1 94,84% | Exportable | No seleccionat |
| CNN deep | F1 96,02% | 55.208 bytes | Seleccionat |
| CNN separable | F1 91,35% | Exportable | No seleccionat |

La decisio final per a l'entrega final es utilitzar `cnn_deep`, perque combina millor rendiment, mida molt baixa i inferencia rapida. El Random Forest es mante com a baseline historic, pero no es el candidat final per a l'app Android.

## 11. RAG per a l'Aplicacio

### 11.1 Problema i Abordatge

El bloc RAG s'ha definit com un sistema documental per interpretar resultats de sessio i donar recomanacions basades en context. L'objectiu no es substituir el model ML, sino complementar-lo: el model classifica activitat i el RAG ajuda a explicar que pot significar una sessio amb molta activitat de repos, alta intensitat aproximada o baixa confianca.

Abast triat per a l'entrega final:

- Recuperar fragments documentals sobre interpretacio del model.
- Explicar limitacions del dataset UCI HAR.
- Proposar recomanacions d'entrenament generals.
- Evitar respostes no suportades pel corpus.

### 11.2 Dades Generades o Usades

S'ha creat una base documental propia amb 12 fragments. Cada fragment conte:

- `id`
- `title`
- `category`
- `text`

Categories principals:

- `training_recommendation`
- `model_interpretation`
- `ml_experiment`
- `rag_design`

Fitxer principal:

`ml/rag/data/knowledge_base.jsonl`

Tambe s'ha creat un conjunt petit d'avaluacio amb 6 preguntes i documents esperats:

`ml/rag/eval/questions.jsonl`

### 11.3 Estructura de Carpetes

```text
ml/rag/
  data/
    knowledge_base.jsonl
  eval/
    questions.jsonl
  scripts/
    evaluate_rag.py
  results/
    rag_evaluation.json
```

### 11.4 Tecnologia Usada

Per mantenir el RAG reproduible i lleuger, s'ha implementat sense serveis externs:

- Python standard library.
- Tokenitzacio simple amb regex.
- Recuperador `keyword_overlap`.
- Recuperador `tfidf_cosine`.
- Generacio extractiva: la resposta es construeix amb els fragments recuperats.

En una versio de producte es podria substituir el recuperador per embeddings semantics i afegir un LLM generatiu, pero per a l'entrega final aquesta versio permet demostrar el flux RAG sense dependencies d'API.

### 11.5 Experimentacio

Script:

`ml/rag/scripts/evaluate_rag.py`

Execucio:

```bash
python ml/rag/scripts/evaluate_rag.py
```

Sortida:

`ml/rag/results/rag_evaluation.json`

Metriques:

- Hit@3: percentatge de preguntes on almenys un document esperat apareix al top 3.
- MRR: mean reciprocal rank, que premia recuperar el document correcte en primera posicio.

### 11.6 Comparacio entre Models de Recuperacio

| Recuperador | Top K | Hit@3 | MRR | Decisio |
| :--- | ---: | ---: | ---: | :--- |
| `keyword_overlap` | 3 | 100% | 0,83 | Baseline |
| `tfidf_cosine` | 3 | 100% | 1,00 | Seleccionat |

El recuperador `tfidf_cosine` es selecciona perque recupera el document esperat en primera posicio per a totes les preguntes d'avaluacio. `keyword_overlap` tambe troba documents rellevants, pero sovint no els ordena tan be.

### 11.7 Resultats Experimentals RAG

Exemples de recuperacio:

| Pregunta | Top documents recuperats |
| :--- | :--- |
| Que vol dir si la sessio te molta alta intensitat? | `kb_003`, `kb_009`, `kb_001` |
| Per que el model pot confondre seure i dret? | `kb_006`, `kb_004`, `kb_005` |
| Quin model final s'ha seleccionat i quines metriques te? | `kb_007`, `kb_004`, `kb_010` |

La primera pregunta recupera fragments sobre alta intensitat aproximada i recomanacio post sessio. La segona recupera la limitacio concreta observada a la matriu de confusio. La tercera recupera les metriques del model final `cnn_deep`.

### 11.8 Discussio RAG

El RAG actual es adequat per al prototip perque:

- Es transparent: es pot inspeccionar quin document dona suport a cada resposta.
- Es reproduible: no depen de credencials ni serveis externs.
- Connecta ML i experiencia d'usuari: interpreta prediccions, confianca i limitacions.
- Es facil d'ampliar: afegir documents nous al JSONL no requereix canviar el codi.

### 11.9 Integracio del RAG a Android

Per connectar el RAG amb l'aplicacio, s'ha afegit una versio local i lleugera dins del paquet Android `ml`. Aquesta integracio reutilitza el mateix enfocament documental de l'experiment Python: documents curts, recuperacio per paraules clau i resposta extractiva basada en fragments.

La pantalla d'historial mostra un boto de resum per a cada sessio amb prediccions ML. Quan l'usuari l'obre, l'app genera una recomanacio post-sessio a partir de:

- activitat dominant de la sessio;
- confianca mitjana del model;
- nombre de prediccions ML;
- nombre de blocs d'alta intensitat;
- fragments documentals locals sobre recuperacio, desplacament suau, alta intensitat, confianca i limitacions UCI HAR.

La resposta mostra una interpretacio de la sessio i les fonts documentals recuperades. Aquesta decisio evita dependencies d'API, funciona offline i mante la tracabilitat entre resultats ML i recomanacions visibles a l'usuari.

### 11.10 Experimentacio amb Ollama i ngrok

Per a la demo final s'ha afegit una via opcional de generacio amb Ollama. L'app permet configurar des de la pantalla de perfil:

- si es vol usar Ollama o el resum local;
- la Base URL d'Ollama, que pot ser local o una URL HTTPS de ngrok;
- el nom del model, per exemple `gemma3:1b`.

Quan Ollama esta activat, l'app envia el resum de sessio i els documents recuperats a l'endpoint `/api/generate`. Si Ollama o ngrok no responen, es guarda automaticament el resum local de fallback. El resum generat queda persistit dins la sessio amb el proveidor, model, latencia i estat de fallback.

L'experimentacio comparativa es documenta a `ml/rag/RAG_EXPERIMENTS.md` i es reprodueix amb:

```bash
python ml/rag/scripts/evaluate_ollama_models.py --models gemma3:1b
```

Resultat actual amb `gemma3:1b`:

| Model | Tasques | Correctes sense error | Latencia mitjana | Grounded overlap | Cobertura termes esperats |
| :--- | ---: | ---: | ---: | ---: | ---: |
| `gemma3:1b` | 10 | 10 | 9266,15 ms | 0,598 | 0,333 |

La lectura experimental es que `gemma3:1b` es viable per demo com a baseline petit, pero la latencia es alta i la cobertura automatica de termes esperats encara es moderada. Per aquest motiu, el sistema de regles continua sent el fallback recomanat.

Limitacions:

- Corpus petit.
- No hi ha embeddings semantics reals.
- La generacio natural amb Ollama es opcional i depen de tenir l'endpoint actiu.
- L'avaluacio usa nomes 6 preguntes.

Per a una versio posterior, el pas natural seria comparar aquest TF-IDF amb embeddings multilingues, ampliar el corpus i substituir la resposta extractiva local per una generacio controlada amb un model de llenguatge.

## 12. Reproduccio

Des de `ml/scripts`:

```bash
python preprocess.py
python train_baseline.py
python train_cnn.py
python train_model_comparison.py
```

Dependencies:

```bash
pip install -r ml/requirements.txt
```

El script `train_model_comparison.py` copia automaticament el model seleccionat a `app/src/main/assets/model_v1.tflite`.

Per reproduir el RAG:

```bash
python ml/rag/scripts/evaluate_rag.py
```

## 13. Conclusions

El pas ML queda complet per a l'entrega final: hi ha baseline, CNN inicial, variants CNN, comparacio amb metriques reals, matriu de confusio, exportacio TFLite i integracio amb l'app. A mes, s'ha incorporat un RAG documental reproduible per interpretar resultats i recomanacions.

La limitacio principal continua sent el dataset: UCI HAR valida la classificacio d'activitat i el flux tecnic, pero una versio final de producte hauria d'entrenar-se amb dades reals de futbolistes i etiquetes especifiques del domini. En el mateix sentit, el RAG actual valida l'arquitectura documental, pero en una versio de produccio caldria ampliar el corpus i avaluar embeddings semantics.
