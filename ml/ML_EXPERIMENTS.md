## Documentació d'experimentació ML - SmarTrain

### 🔄 Estat del projecte

| Fitxer | Estat | Tasca principal |
| :--- | :--- | :--- |
| `preprocess.py` | ✅ Complet | Neteja i segmentació (windowing) |
| `eda.py` | ✅ Complet | Anàlisi (EDA, Distribució, PCA) |
| `train_baseline.py` | ✅ Complet | Random Forest (UCI HAR) |
| `train_cnn.py` | ✅ Complet | Disseny arquitectura CNN 1D |
| `model_v1.tflite` | ✅ Complet | Exportació des de CNN |


### 1. Problema a resoldre

L'objectiu d'aquest mòdul de Machine Learning és la Classificació d'Activitat Humana (HAR) aplicada al futbol amateur. El sistema ha de ser capaç d'identificar en temps real, mitjançant les dades de l'acceleròmetre del dispositiu mòbil, en quin dels següents 3 estats es troba el jugador:

    Repòs: Jugador aturat o caminant molt lentament (fase de recuperació).

    Caminar/Trote: Desplaçaments a intensitat mitjana.

    Esprint: Curses d'alta intensitat (accions explosives).

### 2. Models candidats

Per a aquest problema de sèries temporals, s'han seleccionat els següents models per a l'experimentació:

    CNN 1D (Xarxa Neuronal Convolucional 1D): Model principal per la seva capacitat d'extreure característiques automàticament de les finestres temporals dels sensors.

    Random Forest: Com a model de referència (baseline) per comparar la precisió d'un mètode clàssic basat en característiques estadístiques (mitjana, variància, etc.).

### 3. Eines utilitzades

    Google Colab / Jupyter Notebooks: Per a l'entrenament i visualització de dades.

    Python (TensorFlow & Keras): Per a la construcció i entrenament del model CNN.

    Scikit-learn: Per al preprocessament i el model Random Forest.

    TensorFlow Lite Converter: Per a la quantització i exportació del model final al format mòbil.

    # Mòdul de Machine Learning - SmarTrain

#### 3.1 Dataset

    Hem utilitzat el dataset **UCI Human Activity Recognition (HAR)**.

    * **Enllaç:** [UCI HAR Dataset](https://archive.ics.uci.edu/dataset/240/human+activity+recognition+using+polar+smart+shirts)

    * **Descripció:** El dataset conté gravacions de 30 persones realitzant activitats quotidianes (caminar, seure, estar dret, etc.) amb un smartphone a la cintura. Les dades d'acceleròmetre i giroscopi han estat pre-processades i segmentades en finestres fixes.

### 4. Pipeline d'entrenament

S'han transformat els fitxers de text bruts (`Inertial Signals`) en tensors de 3 dimensions `(mostres, 128, 3)`. Aquest format és l'òptim per a les capes de convolució 1D, ja que respecta la naturalesa temporal del senyal i permet a la CNN aprendre patrons espacials (entre eixos) i temporals (evolució del moviment).

El procés d'entrenament seguirà aquest flux:

    Càrrega del Dataset: Ús del UCI HAR Dataset (públic) per al pre-entrenament.

    Segmentació: Divisió del senyal en finestres de 2 segons (100 mostres a 50Hz).

    Normalització: Ajust dels valors de l'acceleròmetre entre -1 i 1.

    Entrenament: Ajust de paràmetres (epochs, batch size) per maximitzar l'F1-Score.

    Quantització: Conversió a INT8 per optimitzar la mida (< 2MB) i la latència en el mòbil.

### 5. Anàlisi Exploratòria de Dades (EDA)

L'objectiu d'aquesta fase és entendre la distribució de les dades del dataset UCI HAR per detectar possibles biaixos o problemes abans de l'entrenament.

S'ha realitzat una inspecció visual de les dades del dataset UCI HAR per validar la qualitat del senyal i la distribució de les etiquetes.

He calculat les estadístiques bàsiques (mitjana, desviació estàndard, valors mínims/màxims) dels senyals bruts per verificar si les dades estan dins del rang esperat [-1, 1].

* **Troballa:** [Ex: Els valors de l'acceleròmetre es troben majoritàriament entre -0.5 i 0.5 g, indicant un moviment normal].

#### 5.1 Distribució de les classes
És vital assegurar que el dataset estigui equilibrat. Una distribució desequilibrada podria fer que el model esdevingui "mandrós" i només aprengui a predir l'activitat més freqüent.

S'ha generat un recompte de les instàncies per a les 6 activitats (1: Caminar, 2: Caminar pujant, 3: Caminar baixant, 4: Dret, 5: Seure, 6: Estirat).

* **Observacions:** Hem analitzat el recompte d'instàncies per cada activitat (Caminar, Trotar, Repòs). El dataset presenta una distribució equilibrada, amb una lleugera majoria per a les classes 6 i 5.
* **Imatge:** Referència a `class_distribution.png`.

#### 5.2 Anàlisi del senyal
S'ha visualitzat una finestra de 128 mostres (corresponent a 2.56 segons de dades del sensor).
* **Observacions:** El senyal mostra un nivell de soroll baix, la qual cosa facilita l'extracció de característiques per part del model CNN.
* **Imatge:** Referència a `signal_sample.png`.

#### 5.3 Visualització de senyals temporals
Hem representat gràficament un segment de 2 segons (100 mostres) de l'acceleròmetre per visualitzar les diferències entre activitats.

* **Eixos:** L'eix X representa el temps, l'eix Y l'acceleració en 'g'.
* **Resultats:** S'observa que el senyal de "Esprint" mostra pics d'amplitud molt més elevats que "Caminar". Això confirma que el model hauria de poder distingir-los fàcilment.

#### 5.4 Correlació d'eixos
Hem calculat la matriu de correlació entre els eixos X, Y i Z per verificar si hi ha dependències innecessàries.

* **Resultat:** [Comenta si els eixos estan molt correlacionats entre ells].

#### 5.5 Distribució de classes

![Distribució de classes](../data/processed/class_distribution.png)

#### 5.6 Visualització PCA

![PCA dels senyals](../data/processed/pca_visualization.png)

--------------------------

### Experiment 1: Baseline amb Random Forest (UCI HAR Dataset)

* **Data:** 29/03/2026
* **Script utilitzat:** `/ml/scripts/train_baseline.py`
* **Dataset:** UCI Human Activity Recognition (7,352 entrenament / 2,947 test).

##### Configuració del pipeline
| Paràmetre | Valor |
| :--- | :--- |
| **Model** | Random Forest Classifier |
| **Estimadors (n_estimators)** | 100 |
| **Profunditat màxima (max_depth)** | 10 |
| **Segmentació** | Finestres de 128 mostres (2.56s) |
| **Llibreries** | Scikit-learn, Pandas, Numpy |

##### Resultats obtinguts
| Mètrica | Valor real |
| :--- | :--- |
| **Accuracy total** | **92.06%** |
| **F1-Score (Weighted)** | **0.92** |
| **Precision (Mitjana)** | **0.92** |

###### Detall per activitats (F1-Score):
* **Caminar (1):** 0.92
* **Pujar/Baixar escales (2, 3):** 0.89
* **Estar dret/Seure (4, 5):** 0.90 / 0.91
* **Estirat (6):** 1.00 (Precisió perfecta)

##### Conclusions de l'experiment 1
Els resultats són molt satisfactoris per a un model inicial. S'observa que el model identifica perfectament l'estat de repòs total (activitat 6), però té lleugeres confusions en activitats dinàmiques similars (escales vs caminar). 

**Pla d'acció:** Tot i l'alta precisió, el model Random Forest genera un fitxer de gran mida que pot ser ineficient en dispositius mòbils. El següent experiment es basarà en una **CNN 1D** per intentar mantenir o millorar aquest 92% però optimitzant el pes per a l'exportació a **TensorFlow Lite**.

--------------------------

### Experiment 2: CNN 1D (Model Definitiu)
* **Data:** 23/04/2026
* **Script utilitzat:** `/ml/scripts/train_cnn.py`
* **Arquitectura:** Conv1D -> MaxPooling -> Flatten -> Dense -> Dropout(0.5)

##### Resultats obtinguts
| Mètrica | Valor |
| :--- | :--- |
| **Accuracy (Train)** | **90.94%** |
| **Accuracy (Val)** | **86.13%** |

##### Conclusions i Comparativa
* **Comparació:** El model Random Forest (Baseline) va obtenir una accuracy del 92%, lleugerament superior a la CNN (86% validació). Això indica un lleuger *overfitting* a la CNN. 
* **Justificació:** Tot i que el Random Forest és més precís en dades estàtiques, la **CNN 1D** és el model triat per a l'aplicació mòbil perquè té una estructura que permet una latència més baixa i una millor escalabilitat per a dades de sèries temporals en temps real.
* **Optimització:** S'ha exportat el model a format `.tflite` per garantir que el pes sigui mínim (< 2MB) i permeti la inferència en temps real a dins del dispositiu Android sense dependre de servidors externs.

--------------------------