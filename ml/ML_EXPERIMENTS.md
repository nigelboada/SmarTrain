1. Problema a resoldre

L'objectiu d'aquest mòdul de Machine Learning és la Classificació d'Activitat Humana (HAR) aplicada al futbol amateur. El sistema ha de ser capaç d'identificar en temps real, mitjançant les dades de l'acceleròmetre del dispositiu mòbil, en quin dels següents 3 estats es troba el jugador:

    Repòs: Jugador aturat o caminant molt lentament (fase de recuperació).

    Caminar/Trote: Desplaçaments a intensitat mitjana.

    Esprint: Curses d'alta intensitat (accions explosives).

2. Models Candidats

Per a aquest problema de sèries temporals, s'han seleccionat els següents models per a l'experimentació:

    CNN 1D (Xarxa Neuronal Convolucional 1D): Model principal per la seva capacitat d'extreure característiques automàticament de les finestres temporals dels sensors.

    Random Forest: Com a model de referència (baseline) per comparar la precisió d'un mètode clàssic basat en característiques estadístiques (mitjana, variància, etc.).

3. Herramientas utilizadas

    Google Colab / Jupyter Notebooks: Per a l'entrenament i visualització de dades.

    Python (TensorFlow & Keras): Per a la construcció i entrenament del model CNN.

    Scikit-learn: Per al preprocessament i el model Random Forest.

    TensorFlow Lite Converter: Per a la quantització i exportació del model final al format mòbil.

4. Pipeline de entrenamiento

El procés d'entrenament seguirà aquest flux:

    Càrrega del Dataset: Ús del UCI HAR Dataset (públic) per al pre-entrenament.

    Segmentació: Divisió del senyal en finestres de 2 segons (100 mostres a 50Hz).

    Normalització: Ajust dels valors de l'acceleròmetre entre -1 i 1.

    Entrenament: Ajust de paràmetres (epochs, batch size) per maximitzar l'F1-Score.

    Quantització: Conversió a INT8 per optimitzar la mida (< 2MB) i la latència en el mòbil.
