## Documentació d'experimentació ML - SmarTrain

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

### 4. Pipeline d'entrenament

El procés d'entrenament seguirà aquest flux:

    Càrrega del Dataset: Ús del UCI HAR Dataset (públic) per al pre-entrenament.

    Segmentació: Divisió del senyal en finestres de 2 segons (100 mostres a 50Hz).

    Normalització: Ajust dels valors de l'acceleròmetre entre -1 i 1.

    Entrenament: Ajust de paràmetres (epochs, batch size) per maximitzar l'F1-Score.

    Quantització: Conversió a INT8 per optimitzar la mida (< 2MB) i la latència en el mòbil.

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
