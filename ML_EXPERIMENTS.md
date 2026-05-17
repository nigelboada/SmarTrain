# ML_EXPERIMENTS.md

## 1. Descripcion Del Problema

SmarTrain busca registrar sesiones deportivas desde un movil y enriquecerlas con dos capas de inteligencia:

- un modelo ML embarcado que interpreta ventanas cortas de movimiento a partir del acelerometro;
- un sistema RAG que convierte el resumen ML de la sesion en una recomendacion explicable y prudente.

El objetivo no es diagnosticar rendimiento deportivo profesional, sino validar un flujo completo app-modelo-IA: capturar datos, ejecutar inferencia, guardar resultados, recuperar conocimiento relevante y generar una interpretacion post-sesion.

## 2. Dataset Utilizado

Dataset principal: UCI Human Activity Recognition using Smartphones.

Caracteristicas:

| Elemento | Valor |
| --- | ---: |
| Muestras totales | 10.299 |
| Train original | 7.352 |
| Test original | 2.947 |
| Frecuencia | 50 Hz |
| Ventana | 128 muestras |
| Duracion aproximada ventana | 2,56 s |

Clases UCI HAR usadas:

- Walking
- Walking upstairs
- Walking downstairs
- Sitting
- Standing
- Laying

Mapeo a categorias SmarTrain:

| UCI HAR | SmarTrain |
| --- | --- |
| Walking | Desplacament suau |
| Walking upstairs | Alta intensitat |
| Walking downstairs | Alta intensitat |
| Sitting | Repos |
| Standing | Repos |
| Laying | Repos |

Limitacion: UCI HAR no contiene acciones especificas de futbol como sprint, cambio de direccion, presion, conduccion o golpeo. Por eso, las categorias de SmarTrain son una aproximacion tecnica.

## 3. Preprocesamiento

Script principal:

```text
ml/scripts/preprocess.py
```

Entradas principales:

```text
ml/data/raw/
ml/data/raw/train/Inertial Signals/
```

Salidas:

```text
ml/data/processed/X_train.npy
ml/data/processed/y_train.npy
```

Proceso:

- lectura de senales inerciales;
- seleccion de aceleracion total en tres ejes;
- construccion de ventanas compatibles con el modelo Android;
- normalizacion y preparacion para entrenamiento;
- generacion de visualizaciones exploratorias.

## 4. Modelos Evaluados

Scripts:

```text
ml/scripts/train_baseline.py
ml/scripts/train_cnn.py
ml/scripts/train_model_comparison.py
```

Modelos evaluados:

| Modelo | Objetivo |
| --- | --- |
| Random Forest | Baseline clasico |
| CNN inicial | Primera CNN 1D |
| cnn_lite | Modelo pequeno para movil |
| cnn_deep | Mejor capacidad |
| cnn_separable | Reducir parametros |

Resultados principales:

| Modelo | Resultado |
| --- | ---: |
| Random Forest | Accuracy 92,06% |
| CNN inicial | Validation accuracy 86,13% |
| cnn_lite | F1 weighted 94,84% |
| cnn_deep | F1 weighted 96,02% |
| cnn_separable | F1 weighted 91,35% |

## 5. Ajuste De Hiperparametros

Se compararon arquitecturas CNN con distinto tamano y complejidad:

- profundidad de red;
- numero de filtros;
- convoluciones estandar frente a separables;
- balance entre precision, tamano exportado e inferencia movil.

La decision final priorizo:

- rendimiento alto;
- tamano bajo;
- inferencia rapida;
- exportacion estable a TensorFlow Lite;
- integracion sencilla en Android.

## 6. Modelo Final Seleccionado

Modelo final: `cnn_deep`.

| Metrica | Valor |
| --- | ---: |
| Accuracy test | 96,01% |
| F1 weighted | 96,02% |
| Tamano TFLite | 55.208 bytes |
| Inferencia media TFLite | 0,078 ms |

Archivos:

```text
ml/models/model_v1.tflite
app/src/main/assets/model_v1.tflite
```

Hash validado:

```text
3A0176C01ACE86F258E87B9B60763C44A2216AC70A04F0A3D685210577BC00B8
```

## 7. Integracion En Android

Componentes:

| Archivo | Funcion |
| --- | --- |
| `ActivityClassifier.kt` | Carga y ejecuta TFLite |
| `ActivityRecognitionState.kt` | Agrega predicciones y resumen ML |
| `TrackingService.kt` | Captura sensores en segundo plano |
| `Session.kt` | Persiste metricas ML y RAG |
| `SessionDetailScreen.kt` | Muestra resultados al usuario |

Flujo:

```text
SensorProvider
  -> TrackingService
  -> ventana de acelerometro
  -> ActivityClassifier TFLite
  -> ActivityRecognitionState
  -> resumen de sesion
  -> RAG / IA
  -> Room + Firestore
  -> pantalla de detalle
```

## 8. RAG Para La Aplicacion

### Problema

El modelo ML produce etiquetas y estadisticas, pero el usuario necesita una interpretacion comprensible. El RAG aporta contexto documental para explicar:

- que significa la actividad dominante;
- como interpretar la confianza del modelo;
- que limitaciones tiene UCI HAR aplicado a futbol;
- que recomendacion prudente hacer despues de la sesion.

### Datos Usados

Corpus principal:

```text
ml/rag/data/knowledge_base.jsonl
```

Fuentes adicionales para Chroma:

```text
docs/PROJECT_REPORT.md
ml/rag/eval/sessions.jsonl
ml/rag/results/*.md
```

Index validado:

| Elemento | Valor |
| --- | ---: |
| Raw documents | 17 |
| Chunks | 42 |
| Embedding dimension | 768 |
| Vectorstore | ChromaDB |
| Embedding seleccionado | nomic-embed-text |

### Estructura De Carpetas RAG

```text
ml/rag/
  data/knowledge_base.jsonl
  eval/questions.jsonl
  eval/sessions.jsonl
  scripts/build_vector_index.py
  scripts/export_projector.py
  scripts/compare_embedding_models.py
  scripts/compare_generation_models.py
  scripts/rag_backend.py
  chroma_db/smartrain/
  results/
```

### Tecnologia Usada

| Capa | Tecnologia |
| --- | --- |
| Vectorstore | ChromaDB |
| Embeddings | Ollama `nomic-embed-text` |
| Backend | FastAPI |
| Generacion | Ollama Cloud `qwen3-coder-next` |
| App | Android Kotlin + Jetpack Compose |
| Fallback | Reglas locales |

## 9. Experimentacion RAG

### Retrieval Clasico

Script:

```text
ml/rag/scripts/evaluate_rag.py
```

| Recuperador | Top K | Hit@3 | MRR | Decision |
| --- | ---: | ---: | ---: | --- |
| keyword_overlap | 3 | 100% | 0,83 | Baseline |
| tfidf_cosine | 3 | 100% | 1,00 | Mejor baseline clasico |

### Comparacion De Embeddings

Script:

```powershell
python ml/rag/scripts/compare_embedding_models.py --ollama-base-url http://127.0.0.1:11434
```

| Modelo | Hit@6 | MRR | Decision |
| --- | ---: | ---: | --- |
| nomic-embed-text | 0,833 | 0,708 | Seleccionado |
| sentence-transformers/all-MiniLM-L6-v2 | 0,833 | 0,694 | Alternativa viable |

`nomic-embed-text` se mantiene porque empata en Hit@6 y obtiene mejor MRR.

### TensorFlow Projector

Export:

```powershell
python ml/rag/scripts/export_projector.py --store chroma
```

Archivos:

```text
ml/rag/results/projector/vectors.tsv
ml/rag/results/projector/metadata.tsv
ml/rag/results/projector/pca_preview.png
```

Validado manualmente:

- 42 puntos;
- 768 dimensiones;
- PCA, t-SNE y UMAP;
- color por `category` y `source`.

## 10. Comparacion Entre Modelos Usando El RAG

Script:

```powershell
python ml/rag/scripts/compare_generation_models.py --timeout 180
```

Modelos comparados:

| Proveedor | Modelo | OK | Errores | Latencia media | Decision |
| --- | --- | ---: | ---: | ---: | --- |
| cloud | gemma3:4b | 10/10 | 0 | 702,98 ms | Rapido, demasiado breve |
| local | gemma4:latest | 10/10 | 0 | 43.190,86 ms | Calidad correcta, lento |
| cloud | qwen3-coder-next | 10/10 | 0 | 3.378,32 ms | Seleccionado |
| local | gemma3:1b | 10/10 | 0 | 8.022,97 ms | Viable local, menor calidad |
| local | qwen3.6:latest | 9/10 | 1 | 63.571,25 ms | Lento |
| cloud | gpt-oss:20b | 8/10 | 2 | 1.554,34 ms | Respuestas vacias |

Decision final: `qwen3-coder-next` via Ollama Cloud.

Motivos:

- completa 10/10 tareas;
- latencia aceptable;
- buena utilidad de recomendacion;
- buen respeto al contexto;
- integracion directa en app y backend.

## 11. Funcionalidad RAG Propia En La App

La funcionalidad propia del RAG no es solo el texto generado. El modo `Backend RAG` aporta una experiencia que el modo cloud directo no tiene:

- recupera chunks reales desde ChromaDB;
- genera la respuesta con contexto recuperado;
- guarda fuentes, categorias, scores y textos;
- muestra los elementos de contexto en la ficha de sesion;
- permite pulsar cada fuente para ver el chunk ampliado.

Esto convierte el resumen en una recomendacion explicable: el usuario no solo ve una respuesta IA, tambien ve de que conocimiento procede.

## 12. Discusion De Resultados

El sistema ML cumple el objetivo tecnico de clasificar actividad desde sensores moviles y ejecutarse localmente con TFLite. El rendimiento experimental es alto, aunque la transferencia a futbol real esta limitada por UCI HAR.

El RAG mejora la utilidad del resumen post-sesion porque limita la generacion a contexto controlado. El fallback local mantiene la app funcional si no hay red, si falla Ollama o si el backend no esta disponible.

El backend RAG es la opcion mas completa para demo porque ensena trazabilidad: respuesta, modelo, latencia, fuentes, chunks y scores.

## 13. Conclusiones

- La app integra el modelo ML final en Android.
- El flujo sensor -> modelo -> resumen -> persistencia -> UI funciona end-to-end.
- El modelo final `cnn_deep` esta exportado a TensorFlow Lite.
- El RAG propio esta implementado con ChromaDB, FastAPI y Ollama.
- `qwen3-coder-next` es el modelo generativo seleccionado.
- El modo Backend RAG funciona en movil fisico con la URL LAN del PC.
- La documentacion y scripts permiten reproducir los experimentos principales.

## 14. Reproduccion Rapida

Tests Android:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Backend RAG:

```powershell
python -m uvicorn ml.rag.scripts.rag_backend:app --host 0.0.0.0 --port 8000
```

Health:

```text
http://192.168.1.14:8000/health
```

Demo RAG:

```text
http://192.168.1.14:8000/rag/demo-session-summary
```

Comparativas:

```powershell
python ml/rag/scripts/compare_embedding_models.py --ollama-base-url http://127.0.0.1:11434
python ml/rag/scripts/compare_generation_models.py --timeout 180
```
