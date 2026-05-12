# SmarTrain

SmarTrain es una aplicacio Android per monitoritzar sessions esportives i enriquir-les amb analisi ML local. L'app captura accelerometre i localitzacio, executa un model TensorFlow Lite al dispositiu, guarda les sessions amb Room, sincronitza amb Firebase Firestore i mostra una recomanacio post-sessio basada en un RAG local.

## Funcionalitats

- Autenticacio d'usuaris amb Firebase Authentication.
- Registre de sessions esportives amb foreground service.
- Captura de localitzacio i accelerometre.
- Inferencia ML local amb TensorFlow Lite.
- Persistencia local amb Room.
- Sincronitzacio remota amb Cloud Firestore.
- Historial de sessions amb resum ML.
- RAG local per interpretar resultats i donar recomanacions post-sessio.

## Arquitectura

```text
SmarTrain/
  app/
    src/main/java/com/udl/smartrain/
      data/
        local/          Room, sensors i localitzacio
        repository/     sincronitzacio Room/Firestore
      domain/model/     model de domini Session
      ml/               classificador TFLite i RAG local
      service/          TrackingService i estat de sessio
      ui/               pantalles Jetpack Compose
      MainActivity.kt
    src/main/assets/
      model_v1.tflite
  ml/
    data/
      raw/
      processed/
    models/
      model_v1.tflite
      model_cnn.h5
    rag/
      data/
      eval/
      scripts/
      results/
    results/
    scripts/
    ML_EXPERIMENTS.md
```

## Prerequisits

Per executar l'app:

- Android Studio Ladybug o superior.
- JDK compatible amb Gradle del projecte.
- Dispositiu Android real amb Android 10+ recomanat.
- Projecte Firebase configurat.
- Fitxer `app/google-services.json` present.
- Firebase Authentication habilitat amb email/contrasenya.
- Cloud Firestore creat en mode natiu.

Per reproduir ML:

- Python 3.10+ recomanat.
- Dependencies de `ml/requirements.txt`.
- Dataset UCI HAR ubicat dins de `ml/data/raw/train/`.

## Configuracio Firebase

El projecte Android utilitza el paquet:

```text
com.udl.smartrain
```

Firestore ha de permetre que cada usuari autenticat llegeixi i escrigui les seves sessions. Exemple de rules:

```js
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {
    match /sessions/{sessionId} {
      allow create: if request.auth != null
        && request.resource.data.userId == request.auth.uid;

      allow read, update, delete: if request.auth != null
        && resource.data.userId == request.auth.uid;
    }
  }
}
```

Despres de publicar les rules, l'app crea automaticament la col.leccio `sessions` quan es guarda una sessio.

## Executar l'app Android

Des de l'arrel del projecte:

```bash
./gradlew :app:assembleDebug
```

En Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

Tambe es pot obrir el projecte amb Android Studio i executar `app` sobre un dispositiu real.

Flux validat:

1. Iniciar sessio o crear compte.
2. Obrir una nova sessio.
3. Concedir permisos de localitzacio i notificacions.
4. Comencar la sensoritzacio.
5. Veure prediccions ML en directe.
6. Finalitzar i guardar.
7. Consultar l'historial.
8. Revisar la sessio a Room i Firestore.
9. Obrir el resum RAG amb la icona d'informacio.

## Permisos Android

L'app declara:

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `POST_NOTIFICATIONS`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_LOCATION`
- `INTERNET`

La localitzacio es obligatoria per iniciar el tracking. En Android 13+, l'app tambe demana notificacions perque el foreground service pugui mostrar la notificacio persistent.

## Flux App-Model

El flux end-to-end es:

```text
SensorProvider
  -> TrackingService
  -> buffer de 128 mostres
  -> preprocessament Android
  -> ActivityClassifier TensorFlow Lite
  -> ActivityRecognitionState
  -> UI en temps real
  -> resum de sessio
  -> Room
  -> Firestore
```

Preprocessament aplicat a Android:

- conversio de m/s2 a unitats `g`;
- mostreig aproximat a 50 Hz amb `SensorEvent.timestamp`;
- finestres lliscants de 128 mostres;
- normalitzacio basica de l'eix dominant de gravetat;
- deteccio de repos quan la magnitud de l'acceleracio es estable;
- mapatge de 6 classes UCI HAR a 3 categories SmarTrain.

Categories finals mostrades a l'usuari:

| Classe UCI HAR | Categoria SmarTrain |
| :--- | :--- |
| Caminar | Desplacament suau |
| Pujar escales | Alta intensitat |
| Baixar escales | Alta intensitat |
| Seure | Repos |
| Dret | Repos |
| Estirat | Repos |

## Executar preprocessament ML

Instal.lar dependencies:

```bash
pip install -r ml/requirements.txt
```

Executar preprocessament:

```bash
cd ml/scripts
python preprocess.py
```

Sortides:

```text
ml/data/processed/X_train.npy
ml/data/processed/y_train.npy
```

## Entrenar models

Des de `ml/scripts`:

```bash
python train_baseline.py
python train_cnn.py
python train_model_comparison.py
```

El script principal per a la comparacio final es:

```bash
python train_model_comparison.py
```

Aquest script entrena i compara:

- `cnn_lite`
- `cnn_deep`
- `cnn_separable`

La seleccio es fa segons F1 weighted.

## Exportar TensorFlow Lite

`train_model_comparison.py` exporta automaticament el millor model a:

```text
ml/models/model_v1.tflite
app/src/main/assets/model_v1.tflite
```

El fitxer dins de `app/src/main/assets/` es el que carrega Android amb TensorFlow Lite.

## Reproduir RAG experimental

El RAG experimental esta a:

```text
ml/rag/
  data/knowledge_base.jsonl
  eval/questions.jsonl
  scripts/evaluate_rag.py
  results/rag_evaluation.json
```

Executar:

```bash
python ml/rag/scripts/evaluate_rag.py
```

El script compara recuperadors simples i genera:

```text
ml/rag/results/rag_evaluation.json
```

## RAG integrat a l'app

A Android hi ha una versio local i lleugera del RAG a:

```text
app/src/main/java/com/udl/smartrain/ml/SessionRagRecommender.kt
```

Quan l'usuari obre l'historial, cada sessio amb prediccions ML mostra una icona d'informacio. Aquesta accio genera una recomanacio post-sessio a partir de:

- activitat dominant;
- confianca mitjana;
- nombre de prediccions;
- nombre de blocs d'alta intensitat;
- fragments documentals locals.

La resposta mostra interpretacio i fonts recuperades.

## Resultats validats

Validacio en dispositiu real:

| Component | Estat |
| :--- | :--- |
| Login Firebase | OK |
| Permisos Android | OK |
| Foreground service | OK |
| Prediccions ML en directe | OK |
| Guardat local Room | OK |
| Sincronitzacio Firestore | OK |
| Historial de sessions | OK |
| Resum RAG post-sessio | OK |

Resultat ML final:

| Model | Accuracy | F1 weighted | Mida TFLite |
| :--- | ---: | ---: | ---: |
| `cnn_deep` | 96,01% | 96,02% | 55.208 bytes |

Mes detalls a `ml/ML_EXPERIMENTS.md`.

## Limitacions conegudes

- El model esta entrenat amb UCI HAR, no amb dades reals de futbol.
- Les categories d'alta intensitat son una aproximacio basada en pujar/baixar escales.
- La posicio del mobil al cos pot afectar la prediccio.
- El preprocessament Android redueix diferencies d'escala i orientacio, pero no elimina completament el canvi de domini.
- El RAG integrat es local, extractiu i amb corpus petit.
- No hi ha embeddings semantics ni LLM generatiu dins de l'app.
- Les recomanacions son orientatives i no substitueixen criteri professional d'entrenament.

## Fora d'abast actual

El prototip actual no implementa encara:

- integracio Wear OS;
- captura real de frequencia cardiaca;
- notificacions push remotes;
- emmagatzematge de rutes GPS completes punt a punt.

La localitzacio s'utilitza per calcular distancia aproximada durant la sessio, pero la sessio guardada nomes persisteix el resum de distancia, durada i resultats ML. El camp `avgBpm` existeix al model de dades com a extensio futura, pero actualment no es calcula ni es mostra com a metrica funcional.

## Fitxers principals

- `app/src/main/java/com/udl/smartrain/service/TrackingService.kt`
- `app/src/main/java/com/udl/smartrain/data/local/SensorProvider.kt`
- `app/src/main/java/com/udl/smartrain/ml/ActivityClassifier.kt`
- `app/src/main/java/com/udl/smartrain/ml/SessionRagRecommender.kt`
- `app/src/main/java/com/udl/smartrain/data/repository/SessionRepository.kt`
- `ml/scripts/train_model_comparison.py`
- `ml/rag/scripts/evaluate_rag.py`
- `ml/ML_EXPERIMENTS.md`
