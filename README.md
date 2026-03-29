# SmarTrain - Sistema de monitorització de rendiment

Repositori per al projecte SmarTrain de l'assignatura Plataformes en Xarxa.

SmarTrain és una plataforma de seguiment esportiu per a futbolistes que combina la captura de dades sensorials amb Intel·ligència Artificial per analitzar la càrrega física i el posicionament en temps real.

## Arquitectura general del sistema 

El projecte segueix una arquitectura Client-Servidor amb processament a l'extrem (Edge Computing):

    Client (Android App): Desenvolupada en Kotlin seguint el patró MVVM (Model-View-ViewModel).

    Edge IA: El mòdul de TensorFlow Lite s'executa localment al dispositiu per classificar el moviment sense dependre de la xarxa.

    Backend (Firebase): Utilitzat per a l'autenticació, la persistència de dades (Cloud Firestore) i les notificacions push.

    Wearable: Integració amb Wear OS per a la captura de la freqüència cardíaca via Bluetooth.

## Estructura del projecte 

El repositori està organitzat segons els estàndards de l'assignatura:

    /app: Conté el projecte d'Android Studio, la interfície d'usuari (UI), i els serveis de captura (GPS/Acceleròmetre).

    /ml: Conté tot el cicle de vida de la Intel·ligència Artificial: datasets, notebooks d'experimentació i models TFLite.

## Instruccions per executar l'app 

    Clonar el repositori: git clone https://github.com/el-teu-usuari/SmarTrain.git.

    Obrir la carpeta /app amb Android Studio (versió Ladybug o superior).

    Configurar el fitxer google-services.json de Firebase a la carpeta app/.

    Compilar i executar en un dispositiu físic amb Android 10+ (necessari per al Foreground Service).
