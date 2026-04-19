## SmarTrain - Sistema de monitorització de rendiment

Repositori per al projecte SmarTrain de l'assignatura Plataformes en Xarxa.

SmarTrain és una plataforma de seguiment esportiu per a futbolistes que combina la captura de dades sensorials amb Intel·ligència Artificial per analitzar la càrrega física i el posicionament en temps real.

### Arquitectura general del sistema 

El projecte segueix una arquitectura Client-Servidor amb processament a l'extrem (Edge Computing):

    Client (Android App): Desenvolupada en Kotlin seguint el patró MVVM (Model-View-ViewModel).

    Edge IA: El mòdul de TensorFlow Lite s'executa localment al dispositiu per classificar el moviment sense dependre de la xarxa.

    Backend (Firebase): Utilitzat per a l'autenticació, la persistència de dades (Cloud Firestore) i les notificacions push.

    Wearable: Integració amb Wear OS per a la captura de la freqüència cardíaca via Bluetooth.

### Estructura del projecte 

El repositori està organitzat segons els estàndards de l'assignatura:

    /app: Conté el projecte d'Android Studio, la interfície d'usuari (UI), i els serveis de captura (GPS/Acceleròmetre).

    /ml: Conté tot el cicle de vida de la Intel·ligència Artificial: datasets, notebooks d'experimentació i models TFLite.


#### Descripció del Backend

El backend de SmarTrain s'ha implementat utilitzant Firebase (Google Cloud Platform), escollit per la seva capacitat de sincronització en temps real i la seva escalabilitat. Els serveis utilitzats són:

    Cloud Firestore: Base de dades NoSQL basada en documents per a l'emmagatzematge de les sessions d'entrenament, rutes GPS i mètriques de rendiment.

    Firebase Authentication: Gestió del registre i inici de sessió d'usuaris de forma segura.

#### Flux de dades del sistema

El sistema segueix un flux circular per garantir la integritat de les dades:

    Captura: El LocationProvider i el SensorProvider recullen dades en brut (GPS, ritme cardíac).

    Processament: El MainViewModel rep les dades i actualitza l'estat de la UI.

    Persistència Local (Room): En finalitzar l'entrenament, el SessionRepository guarda la sessió a la base de dades local SQLite (via Room).

    Sincronització Remota: El Repositori intenta immediatament una operació d'escriptura a Firestore. Si l'operació té èxit, marca la sessió com a isSynced = true.

    Recuperació: En obrir l'historial, l'aplicació prioritza les dades de Firestore per oferir una experiència multi-dispositiu.




### Instruccions per executar l'app 

    Clonar el repositori: git clone https://github.com/el-teu-usuari/SmarTrain.git.

    Obrir la carpeta /app amb Android Studio (versió Ladybug o superior).

    Configurar el fitxer google-services.json de Firebase a la carpeta app/.

    Compilar i executar en un dispositiu físic amb Android 10+ (necessari per al Foreground Service).
