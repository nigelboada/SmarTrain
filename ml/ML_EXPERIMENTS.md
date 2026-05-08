# Documentacio d'experimentacio ML - SmarTrain

## 1. Problema

SmarTrain necessita classificar activitat humana a partir de l'accelerometre del mobil per enriquir una sessio esportiva amb senyals d'intensitat. L'objectiu funcional de producte es descriu en tres nivells:

- Repos: recuperacio o activitat molt baixa.
- Desplacament suau: caminar o moviment continu moderat.
- Alta intensitat: canvis de ritme o accions explosives.

El model entrenat per a l'entrega 3A utilitza el dataset public UCI HAR. Aquest dataset no conte accions especifiques de futbol i te 6 classes, no 3. Per tant, el model integrat valida el flux ML end-to-end de l'app, pero encara no substitueix un classificador final entrenat amb dades reals de futbol.

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

## 10. Comparacio i Seleccio Final

| Model | Resultat | Mida/exportacio | Decisio |
| :--- | :--- | :--- | :--- |
| Random Forest | Accuracy 92,06% | No exportat a TFLite | Baseline |
| CNN inicial | Validation accuracy 86,13% | 1.041.524 bytes | Substituit |
| CNN lite | F1 94,84% | Exportable | No seleccionat |
| CNN deep | F1 96,02% | 55.208 bytes | Seleccionat |
| CNN separable | F1 91,35% | Exportable | No seleccionat |

La decisio final per a 3A es utilitzar `cnn_deep`, perque combina millor rendiment, mida molt baixa i inferencia rapida. El Random Forest es mante com a baseline historic, pero no es el candidat final per a l'app Android.

## 11. Reproduccio

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

## 12. Conclusions

El pas ML queda complet per a l'entrega 3A: hi ha baseline, CNN inicial, variants CNN, comparacio amb metriques reals, matriu de confusio, exportacio TFLite i integracio amb l'app. La limitacio principal continua sent el dataset: UCI HAR valida la classificacio d'activitat i el flux tecnic, pero una versio final de producte hauria d'entrenar-se amb dades reals de futbolistes i etiquetes especifiques del domini.
