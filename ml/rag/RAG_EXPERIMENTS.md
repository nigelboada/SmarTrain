# SmarTrain RAG experiments

## Objectiu

Comparar diferents models generatius per substituir parcialment el resum RAG basat en regles de l'app. El sistema de regles es manté com a fallback perquè l'app continuï funcionant si el model local/cloud no respon.

## Arquitectura experimental

- Corpus: `ml/rag/data/knowledge_base.jsonl`
- Preguntes d'avaluació: `ml/rag/eval/questions.jsonl`
- Sessions sintètiques: `ml/rag/eval/sessions.jsonl`
- Script principal: `ml/rag/scripts/evaluate_ollama_models.py`
- Resultats:
  - `ml/rag/results/ollama_model_comparison.json`
  - `ml/rag/results/ollama_model_comparison.csv`

El recuperador és TF-IDF cosine, per mantenir el retrieval constant entre models. Així la comparació mesura sobretot la generació final.

## Execució

Amb Ollama local:

```powershell
python ml/rag/scripts/evaluate_ollama_models.py --models gemma3:1b
```

Amb diversos models:

```powershell
python ml/rag/scripts/evaluate_ollama_models.py --models gemma3:1b qwen3.6 gemma4
```

Amb endpoint exposat per ngrok:

```powershell
python ml/rag/scripts/evaluate_ollama_models.py --base-url https://<subdomini-ngrok> --models gemma3:1b
```

## Ús de ngrok

Ngrok pot simplificar les proves amb mòbil físic perquè evita canviar la IP local del PC. La idea és exposar el port d'Ollama:

```powershell
ngrok http 11434
```

Després es configura l'app o l'script amb la URL HTTPS generada. Per a una entrega o demo, és recomanable no deixar l'endpoint obert permanentment i no exposar models o dades sensibles.

## Mètriques

- `latency_ms`: temps de resposta del model.
- `source_hit_rate`: per preguntes amb documents esperats, proporció de fonts esperades recuperades.
- `expected_term_coverage`: per sessions sintètiques, proporció de termes esperats presents a la resposta.
- `grounded_overlap`: solapament lèxic entre resposta i documents recuperats. És una mètrica automàtica orientativa, no substitueix una revisió manual.
- `answer_chars`: mida de la resposta.
- `error`: errors de connexió, model no disponible o timeout.

## Models candidats

Models locals o instal·lables:

- `gemma3:1b`: baseline petit i ràpid.
- `qwen3.6`: candidat petit/mitjà si està instal·lat.
- `gemma4`: candidat més gran si està instal·lat.

Models cloud disponibles des de la UI d'Ollama:

- `qwen3.5:cloud`
- `nemotron-3-super:cloud`
- `gemma4:31b-cloud`
- `qwen3-coder:480b-cloud`

Cal comprovar que la sessió d'Ollama té accés cloud abans d'executar-los des de l'API.

## Criteris de decisió

Un model és millor candidat per integrar a l'app si:

- respon en català de forma estable;
- manté les recomanacions dins del context RAG;
- no inventa mètriques;
- té latència acceptable per una pantalla de resum;
- funciona amb fallback quan l'endpoint no està disponible.

## Estat actual

Model local detectat amb `ollama list`:

| Model | Tipus | Estat |
| --- | --- | --- |
| `gemma3:1b` | local petit | instal·lat i avaluat |

Primera execució completa:

```powershell
python ml/rag/scripts/evaluate_ollama_models.py --models gemma3:1b --timeout 120
```

Resultat resum:

| Model | Tasques | Correctes sense error | Latència mitjana | Grounded overlap | Cobertura termes esperats | Errors |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `gemma3:1b` | 10 | 10 | 9266.15 ms | 0.598 | 0.333 | 0 |

Lectura inicial:

- `gemma3:1b` és usable com a baseline petit.
- La latència és alta per a una resposta immediata dins l'app, però acceptable per a experimentació.
- La cobertura automàtica de termes esperats és baixa/moderada; cal revisar manualment les respostes del JSON abans de triar-lo com a model final.
- El sistema de fallback per regles continua sent necessari per demo i ús mòbil.
