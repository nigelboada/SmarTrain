# Documentacio d'experimentacio ML - SmarTrain

## 1. Problema

SmarTrain necessita classificar activitat humana a partir de l'accelerometre del mobil per enriquir una sessio esportiva amb senyals d'intensitat. L'objectiu funcional de producte es descriu en tres nivells:

- Repos: recuperacio o activitat molt baixa.
- Desplacament suau: caminar o moviment continu moderat.
- Alta intensitat: canvis de ritme o accions explosives.

El model disponible per a l'entrega 3A no esta entrenat encara amb dades reals de futbol. Per aquest motiu s'ha utilitzat UCI HAR com a dataset public de preentrenament i validacio tecnica. UCI HAR te 6 classes, no 3. La integracio Android mostra aquestes 6 classes i les utilitza com a aproximacio inicial:

| Classe UCI HAR | Interpretacio a SmarTrain |
| :--- | :--- |
| Caminar | Desplacament suau |
| Pujar escales | Alta intensitat aproximada |
| Baixar escales | Alta intensitat aproximada |
| Seure | Repos |
| Dret | Repos |
| Estirat | Repos |

La discrepancia queda assumida com una limitacio del prototip: el sistema valida el flux ML end-to-end, pero no substitueix encara un model entrenat amb dades especifiques de futbol.

## 2. Dataset

Dataset utilitzat: UCI Human Activity Recognition using Smartphones.

- Mostres totals: 10.299.
- Entrenament original: 7.352 mostres.
- Test original: 2.947 mostres.
- Frequencia: 50 Hz.
- Finestra: 128 mostres, aproximadament 2,56 segons.
- Sensors: accelerometre i giroscopi; per a la integracio mobil actual s'utilitzen els 3 eixos d'acceleracio total.
- Classes: caminar, pujar escales, baixar escales, seure, dret, estirat.

En aquest repositori hi ha dades processades a:

- `ml/data/processed/X_train.npy`
- `ml/data/processed/y_train.npy`

## 3. Preprocessament

Script principal: `ml/scripts/preprocess.py`.

El preprocessament carrega els fitxers `total_acc_x_train.txt`, `total_acc_y_train.txt` i `total_acc_z_train.txt`, els apila en tensors `(samples, 128, 3)` i ajusta les etiquetes per comencar a 0. Aquest format coincideix amb l'entrada del model TensorFlow Lite integrat a Android: `[1][128][3]`.

Punts importants:

- Les finestres mantenen l'ordre temporal.
- Les dades UCI ja venen normalitzades.
- La inferencia a Android usa finestres lliscants de 128 lectures d'accelerometre.

## 4. Models Avaluats

### Model 1: Random Forest baseline

Script: `ml/scripts/train_baseline.py`.

Model classic sobre caracteristiques tabulars UCI HAR. Serveix com a referencia de precisio, pero no es el candidat final per a mobil per mida i portabilitat.

Resultats historics documentats:

| Metrica | Valor |
| :--- | :--- |
| Accuracy | 92,06% |
| F1 weighted | 0,92 |
| Precision mitjana | 0,92 |

### Model 2: CNN 1D inicial

Script: `ml/scripts/train_cnn.py`.

Arquitectura:

`Conv1D(64, 3)` -> `MaxPooling1D(2)` -> `Flatten()` -> `Dense(64)` -> `Dropout(0.5)` -> `Dense(6, softmax)`

Resultats historics documentats:

| Metrica | Valor |
| :--- | :--- |
| Accuracy train | 90,94% |
| Accuracy validation | 86,13% |
| Exportacio | `ml/models/model_v1.tflite` |
| Mida TFLite actual | 1.041.524 bytes |

### Model 3: variants CNN per a comparacio

Nou script: `ml/scripts/train_model_comparison.py`.

Aquest script afegeix tres variants comparables i selecciona automaticament la millor segons F1 weighted:

| Variant | Arquitectura | Objectiu |
| :--- | :--- | :--- |
| `cnn_lite` | Conv1D + GlobalAveragePooling | Model petit i estable per mobil |
| `cnn_deep` | Conv1D + BatchNorm + mes filtres | Millorar capacitat del model |
| `cnn_separable` | SeparableConv1D | Reduir parametres i cost d'inferencia |

Sortides esperades:

- `ml/results/model_comparison.json`
- `ml/results/confusion_matrix_cnn_lite.png`
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
- Classification report per classe.
- Matriu de confusio.
- Mida del model exportat.
- Temps mitja d'inferencia TFLite.

El nou script calcula les metriques amb un split estratificat 70/15/15 a partir de les dades processades. La matriu de confusio s'exporta com a imatge per facilitar la revisio al document final.

## 7. Comparacio Actual

| Model | Accuracy/F1 disponible | Mida | Apte mobil | Estat |
| :--- | :--- | :--- | :--- | :--- |
| Random Forest | Accuracy 92,06%, F1 0,92 | Alta, no exportat a TFLite | No | Baseline |
| CNN 1D inicial | Val accuracy 86,13% | 1.041.524 bytes | Si | Integrat a Android |
| CNN lite/deep/separable | Calculat per `train_model_comparison.py` | Exportat per script | Si | Preparat per executar |

En aquesta sessio no s'han executat els nous entrenaments perque l'entorn local no te instal.lat TensorFlow ni scikit-learn. El script queda preparat per executar-se en Colab o en un entorn Python amb les dependencies ML.

## 8. Optimitzacio per Mobil

El model final s'exporta a TensorFlow Lite. El script de comparacio aplica `tf.lite.Optimize.DEFAULT` per reduir la mida i preparar inferencia eficient al dispositiu.

La integracio Android carrega `app/src/main/assets/model_v1.tflite` amb `Interpreter`, genera finestres de 128 mostres i publica:

- activitat actual,
- confianca,
- historic recent,
- resum persistent dins la sessio.

## 9. Resultats Experimentals

Resultats confirmats fins ara:

- Random Forest baseline: 92,06% accuracy.
- CNN inicial: 86,13% validation accuracy.
- Model TFLite integrat: 1.041.524 bytes.
- Flux Android: sensors -> buffer -> TFLite -> UI -> Room/Firebase.

Resultats pendents de generar amb `train_model_comparison.py`:

- F1 weighted de `cnn_lite`, `cnn_deep` i `cnn_separable`.
- Matriu de confusio final.
- Temps d'inferencia TFLite en l'entorn d'execucio.

## 10. Justificacio del Model Final

Per a l'entrega 3A es mante la CNN 1D exportada a TFLite com a model final integrat. Tot i que el Random Forest te millor accuracy historica, no es tan adequat per a una app Android amb inferencia local, exportacio senzilla i flux en temps real.

La decisio final es:

- usar CNN 1D per la integracio mobil,
- mantenir Random Forest com a baseline,
- deixar preparades variants CNN per seleccionar una versio millor quan es pugui executar l'entrenament complet,
- documentar que les 6 classes UCI HAR son una aproximacio tecnica al problema de 3 nivells esportius.

## 11. Reproduccio

Des de `ml/scripts`:

```bash
python preprocess.py
python train_baseline.py
python train_cnn.py
python train_model_comparison.py
```

Dependencies necessaries:

- numpy
- pandas
- scikit-learn
- tensorflow
- matplotlib

El script `train_model_comparison.py` copia automaticament el model seleccionat a `app/src/main/assets/model_v1.tflite`.

## 12. Conclusions

El modul ML ja cobreix el cicle minim necessari per a 3A: preprocessament, baseline, CNN exportada, integracio TFLite i documentacio de la discrepancia entre dataset generic i cas futbolistic. La millora principal pendent es executar la comparacio nova en un entorn amb dependencies ML i substituir el model integrat si alguna variant CNN supera la versio actual mantenint una mida i latencia adequades.
