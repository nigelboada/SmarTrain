# SmarTrain RAG experiments

## Objectiu

Comparar diferents models generatius per complementar el resum RAG basat en regles de l'app. El sistema de regles es mante com a fallback perque l'app continui funcionant si el model local o cloud no respon.

## Arquitectura experimental

- Corpus: `ml/rag/data/knowledge_base.jsonl`
- Preguntes d'avaluacio: `ml/rag/eval/questions.jsonl`
- Sessions sintetiques: `ml/rag/eval/sessions.jsonl`
- Script principal: `ml/rag/scripts/evaluate_ollama_models.py`
- Resultats:
  - `ml/rag/results/ollama_model_comparison.json`
  - `ml/rag/results/ollama_model_comparison.csv`

El recuperador es TF-IDF cosine, per mantenir el retrieval constant entre models. Aixi la comparacio mesura sobretot la generacio final.

## Execucio

Amb Ollama local:

```powershell
python ml/rag/scripts/evaluate_ollama_models.py --models gemma3:1b
```

Amb diversos models locals:

```powershell
python ml/rag/scripts/evaluate_ollama_models.py --models gemma3:1b qwen3.6:latest gemma4:latest
```

Amb models cloud d'Ollama, guarda primer la clau en una variable d'entorn i no l'escriguis al repositori. L'avaluador usa `/api/chat`, que funciona tant per local com per cloud:

```powershell
$env:OLLAMA_API_KEY="..."
python ml/rag/scripts/evaluate_ollama_models.py --base-url https://ollama.com --models qwen3-coder-next --timeout 180
```

També es pot usar `https://ollama.com/api` com a base URL; l'script evita duplicar `/api`.

Amb endpoint exposat per ngrok:

```powershell
python ml/rag/scripts/evaluate_ollama_models.py --base-url https://<subdomini-ngrok> --models gemma3:1b
```

## Us de ngrok

Ngrok simplifica les proves amb mobil fisic perque evita canviar la IP local del PC. La idea es exposar temporalment el port d'Ollama:

```powershell
ollama serve
ngrok http 11434
```

Despres es configura l'app o l'script amb la URL HTTPS generada per ngrok. Per a una entrega o demo, es recomanable no deixar l'endpoint obert permanentment i no exposar models o dades sensibles.

## Metriques

- `latency_ms`: temps de resposta del model.
- `source_hit_rate`: per preguntes amb documents esperats, proporcio de fonts esperades recuperades.
- `expected_term_coverage`: per sessions sintetiques, proporcio de termes esperats presents a la resposta.
- `grounded_overlap`: solapament lexic entre resposta i documents recuperats. Es una metrica automatica orientativa, no substitueix una revisio manual.
- `answer_chars`: mida de la resposta.
- `error`: errors de connexio, model no disponible o timeout.

## Models candidats

Models locals o instal.lables:

- `gemma3:1b`: baseline petit i rapid.
- `qwen3.6:latest`: candidat gran local si esta instal.lat.
- `gemma4:latest`: candidat mes gran local si esta instal.lat.

Models cloud disponibles des de la UI d'Ollama:

- `qwen3-coder-next`
- altres models retornats per `https://ollama.com/api/tags`, segons disponibilitat del compte.

Cal comprovar que la sessio d'Ollama te acces cloud abans d'executar-los des de l'API.

## Criteris de decisio

Un model es millor candidat per integrar a l'app si:

- respon en catala de forma estable;
- mante les recomanacions dins del context RAG;
- no inventa metriques;
- te latencia acceptable per una pantalla de resum;
- funciona amb fallback quan l'endpoint no esta disponible.

## Estat actual

Model local detectat amb `ollama list`:

| Model | Tipus | Estat |
| --- | --- | --- |
| `gemma3:1b` | local petit | instal.lat i avaluat |
| `qwen3-coder-next` | cloud | connexio validada amb 1 tasca |

Primera execucio completa:

```powershell
python ml/rag/scripts/evaluate_ollama_models.py --models gemma3:1b --timeout 120
```

Resultat resum:

| Model | Tasques | Correctes sense error | Latencia mitjana | Grounded overlap | Cobertura termes esperats | Errors |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `gemma3:1b` | 10 | 10 | 9266.15 ms | 0.598 | 0.333 | 0 |

Lectura inicial:

- `gemma3:1b` es usable com a baseline petit.
- La latencia es alta per a una resposta immediata dins l'app, pero acceptable per a experimentacio i demo.
- La cobertura automatica de termes esperats es baixa/moderada; cal revisar manualment les respostes del JSON abans de triar-lo com a model final generatiu.
- El sistema de fallback per regles continua sent necessari per demo i us mobil.

## Comparativa de seleccio de model

La comparativa detallada de models locals es documenta a:

```text
ml/rag/results/model_selection_report.md
```

La comparativa automatica completa entre models locals i cloud es documenta a:

```text
ml/rag/results/generation_model_comparison_report.md
```

Execucio utilitzada:

```powershell
python ml/rag/scripts/compare_generation_models.py --timeout 180
```

Models comparats:

| Proveidor | Model | Tasques OK | Errors/timeouts | Latencia mitjana | Respecte RAG | No invencio | Utilitat |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| cloud | `gemma3:4b` | 10/10 | 0 | 702,98 ms | 4,20 | 5,00 | 3,30 |
| local | `gemma4:latest` | 10/10 | 0 | 43.190,86 ms | 3,98 | 5,00 | 3,87 |
| cloud | `qwen3-coder-next` | 10/10 | 0 | 3.378,32 ms | 3,88 | 4,94 | 3,74 |
| local | `gemma3:1b` | 10/10 | 0 | 8.022,97 ms | 3,60 | 4,84 | 3,43 |
| local | `qwen3.6:latest` | 9/10 | 1 | 63.571,25 ms | 3,71 | 5,00 | 3,81 |
| cloud | `gpt-oss:20b` | 8/10 | 2 | 1.554,34 ms | 3,60 | 4,83 | 3,60 |

Decisio final de l'experiment:

- Model recomanat per a la via generativa: `qwen3-coder-next`.
- Motiu: completa 10/10 tasques, te latencia mitjana acceptable per a una pantalla de detall, genera respostes mes utils que `gemma3:4b` i evita els temps massa alts de `gemma4:latest` i `qwen3.6:latest`.
- Model fallback local recomanat: el sistema de regles actual. Si es vol un fallback local generatiu, `gemma3:1b` es el mes viable dels locals per latencia, pero la qualitat es inferior.

Estat actual de decisio:

- `qwen3-coder-next` es el candidat final per a la generacio amb Ollama Cloud.
- `qwen3.6:latest` s'ha de tractar com a comparador local pesat; en CPU ha mostrat latencies molt altes.
- `gemma4:latest` queda descartat com a opcio principal per latencia molt alta, tot i tenir respostes correctes.
- `gemma3:4b` cloud es molt rapid i ben fonamentat, pero dona respostes massa breus i menys utils.

Guia operativa detallada:

```text
ml/rag/MODEL_COMPARISON_GUIDE.md
```
