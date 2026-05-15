# SmarTrain

SmarTrain es una aplicacio Android per monitoritzar sessions esportives i enriquir-les amb analisi ML local i recomanacions post-sessio basades en RAG.

L'app captura accelerometre i localitzacio, executa un model TensorFlow Lite al dispositiu, guarda sessions amb Room, sincronitza amb Firebase Firestore i pot generar un resum IA amb Ollama Cloud o amb fallback local per regles.

## Estat del projecte

| Bloc | Estat |
| --- | --- |
| App Android | Implementada amb Jetpack Compose, Room, Firebase Auth/Firestore i foreground service |
| Classificador ML | Integrat amb TensorFlow Lite |
| RAG local | Integrat i funcional offline |
| IA generativa | Integrada opcionalment via Ollama `/api/chat` |
| Model generatiu seleccionat | `qwen3-coder-next` via Ollama Cloud |
| Fallback | Regles locals persistides amb la sessio |

## Documentacio principal

La documentacio tecnica unificada esta a:

```text
docs/PROJECT_REPORT.md
```

Inclou:

- arquitectura del projecte;
- experimentacio ML amb UCI HAR;
- comparacio del model TensorFlow Lite;
- disseny i avaluacio RAG;
- comparativa de 6 models generatius locals/cloud;
- decisio final del model IA;
- estat de la integracio i punts pendents.

## Execucio rapida

Compilar l'app:

```powershell
.\gradlew.bat :app:assembleDebug
```

Executar tests unitaris:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Reproduir la comparativa generativa:

```powershell
$env:OLLAMA_API_KEY="..."
python ml/rag/scripts/compare_generation_models.py --timeout 180
```

## Resultats principals

Model ML local seleccionat:

| Model | Accuracy | F1 weighted | Mida TFLite |
| --- | ---: | ---: | ---: |
| `cnn_deep` | 96,01% | 96,02% | 55.208 bytes |

Model generatiu seleccionat:

| Model | Proveidor | Tasques OK | Latencia mitjana | Decisio |
| --- | --- | ---: | ---: | --- |
| `qwen3-coder-next` | Ollama Cloud | 10/10 | 3.378,32 ms | Seleccionat |

El fallback local per regles continua sent obligatori per garantir que l'app funciona sense connexio o si Ollama falla.
