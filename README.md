## SmarTrain - Sistema de monitorització de rendiment

Repositori per al projecte SmarTrain de l'assignatura Plataformes en Xarxa.

SmarTrain és una plataforma de seguiment esportiu per a futbolistes que combina la captura de dades sensorials amb Intel·ligència Artificial per analitzar la càrrega física i el posicionament en temps real.




### Arquitectura general del sistema 

El projecte segueix una arquitectura Client-Servidor amb processament a l'extrem (Edge Computing):

    Client (Android App): Desenvolupada en Kotlin seguint el patró MVVM (Model-View-ViewModel).

    Edge IA: El mòdul de TensorFlow Lite s'executa localment al dispositiu per classificar el moviment sense dependre de la xarxa.

    Backend (Firebase): Utilitzat per a l'autenticació, la persistència de dades (Cloud Firestore) i les notificacions push.

    Wearable: Integració amb Wear OS per a la captura de la freqüència cardíaca via Bluetooth.

Arquitectura del sistema:

- **SensorProvider:** Exposa les dades a través d'un `StateFlow` (Programació reactiva).
- **TrackingService:** Consumeix el flux de dades mitjançant `Coroutines`, gestionant el buffer i la inferència en segon pla sense bloquejar l'UI.

### Estructura del projecte 

El repositori està organitzat seguint el patró d'rquitectura Clean Architecture aplicada a MVVM, garantint la separació de responsabilitats:

    SmarTrain/
    ├── app/
    │   ├── src/main/java/com/udl/smartrain/
    │   │   ├── data/
    │   │   │   ├── local/          # Persistència: Room (Entities, DAOs, Converters)
    │   │   │   └── repository/     # Repository Pattern (Sincronització Firebase/Room)
    │   │   ├── domain.model/       # Entitats del domini (Data classes)
    │   │   ├── service/            # Serveis en segon pla (TrackingService, Location)
    │   │   ├── ui/
    │   │   │   ├── screens/        # Interfície d'usuari (Jetpack Compose)
    │   │   │   ├── theme/          # Definició de colors i estils
    │   │   │   └── viewmodel/      # Lògica d'estat (MainViewModel)
    │   │   └── MainActivity.kt     # Punt d'entrada de l'App
    │   └── google-services.json    # Credencials Firebase
    └── ml/
        ├── experiments/            # Notebooks de recerca i datasets
        ├── scripts/                # Scripts de preprocessament i transformació
        └── ML_EXPERIMENTS.md       # Documentació del cicle de vida del model ML

Especificacions:

    /app: Conté el projecte d'Android Studio, la interfície d'usuari (UI), i els serveis de captura (GPS/Acceleròmetre).
    
    /data: Capa de dades. És la responsable de decidir si l'app ha de llegir de la memòria local (Room) o del núvol (Firestore).
    
    /ui: Capa de presentació. Utilitza StateFlow per mantenir la interfície sincronitzada amb les dades en temps real.

    /ml: Mòdul independent que conté tot el cicle de vida de la Intel·ligència Artificial: datasets, notebooks d'experimentació i models TFLite.


#### Descripció del Backend

El backend de SmarTrain s'ha implementat utilitzant Firebase (Google Cloud Platform), escollit per la seva capacitat de sincronització en temps real i la seva escalabilitat. Els serveis utilitzats són:

    Cloud Firestore: Base de dades NoSQL basada en documents per a l'emmagatzematge de les sessions d'entrenament, rutes GPS i mètriques de rendiment.

    Firebase Authentication: Gestió del registre i inici de sessió d'usuaris de forma segura.

#### Flux de dades del sistema

El sistema segueix un flux circular per garantir la integritat de les dades:

    Captura: El LocationProvider i el SensorProvider recullen dades en brut (GPS, ritme cardíac).

    Processament: El MainViewModel rep les dades i actualitza l'estat de la UI.

    Persistència Local (Room): En finalitzar l'entrenament, el SessionRepository guarda la sessió a la base de dades local SQLite (via Room). Això assegura que l'usuari no perdi informació en zones sense cobertura.

    Sincronització Remota: El Repositori intenta immediatament una operació d'escriptura a Firestore. Si l'operació té èxit, marca la sessió com a isSynced = true.

    Recuperació: En obrir l'historial, l'aplicació prioritza les dades de Firestore per oferir una experiència multi-dispositiu.

#### Progrés de desenvolupament

- [x] UI del Dashboard i Perfil
- [x] Servei de seguiment (TrackingService)
- [x] Entrenament model ML (UCI HAR)
- [x] **Integració IA (TFLite):**
    - Dependències afegides.
    - Creada l'arquitectura del paquet `ml`.
    - **Connectat:** Implementat el buffer de dades i la inferència al `TrackingService`.

#### Validacio end-to-end en dispositiu real

S'ha validat el flux complet de l'aplicacio en un dispositiu Android real:

| Prova | Resultat |
| :--- | :--- |
| Login i sessio Firebase | OK |
| Permisos de localitzacio i foreground service | OK |
| Captura de sensor i GPS | OK |
| Inferencia ML en directe amb TensorFlow Lite | OK |
| Finalitzacio de sessio | OK |
| Persistencia local amb Room | OK |
| Visualitzacio de l'historial | OK |
| Sincronitzacio amb Cloud Firestore | OK pendent de revisio visual al panell Firebase |

Durant la prova real s'ha detectat que enviar directament l'accelerometre Android al model provocava prediccions poc realistes, especialment `Baixar escales`, fins i tot amb el mobil quiet. La causa principal era la diferencia d'escala i orientacio entre les dades UCI HAR i el sensor del dispositiu.

S'ha aplicat un preprocessament a l'app abans de la inferencia:

- conversio de m/s2 a unitats `g`;
- normalitzacio basica de l'orientacio dominant de la gravetat per aproximar el format UCI HAR;
- deteccio de repos quan la magnitud de l'acceleracio es estable.

Limitacions actuals:

- el model final esta entrenat amb UCI HAR, no amb dades reals de futbol;
- les classes `Pujar escales` i `Baixar escales` s'interpreten com una aproximacio d'alta intensitat;
- la posicio del mobil al cos encara pot afectar les prediccions;
- els resultats ML s'han de considerar orientatius dins del prototip.

### Instruccions per executar l'app 

    Clonar el repositori: git clone https://github.com/el-teu-usuari/SmarTrain.git.

    Obrir la carpeta /app amb Android Studio (versió Ladybug o superior).

    Configurar el fitxer google-services.json de Firebase a la carpeta app/.

    Compilar i executar en un dispositiu físic amb Android 10+ (necessari per al Foreground Service).
