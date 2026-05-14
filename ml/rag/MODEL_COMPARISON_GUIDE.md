# Guia de comparacio de models generatius - SmarTrain

## Objectiu

Avaluar models generatius per al resum RAG post sessio de SmarTrain de manera reproduible, comparable i defensable a nivell d'enginyeria.

El model final no s'ha de triar nomes per qualitat subjectiva. La decisio ha de combinar:

- estabilitat de connexio;
- latencia;
- respecte pel context RAG;
- qualitat del catala;
- absencia de dades inventades;
- utilitat de la recomanacio;
- compatibilitat amb el flux Android i fallback local.

## Estat actual

La connexio amb `qwen3-coder-next` ha funcionat correctament amb Ollama Cloud i `/api/chat`.

Resultat validat fins ara:

| Model | Tasques | Latencia mitjana | Grounded overlap | Errors | Lectura |
| --- | ---: | ---: | ---: | ---: | --- |
| `qwen3-coder-next` | 1 | 1639.9 ms | 0.700 | 0 | Prova de connexio correcta; falta avaluacio completa. |

Aixo confirma que el nom del model i l'endpoint son correctes, pero encara no dona prou evidencia per declarar-lo guanyador.

## Preparacio

Des de l'arrel del projecte:

```powershell
pip install -r ml/requirements.txt
```

Per a Ollama Cloud, carregar la clau nomes a l'entorn local:

```powershell
$env:OLLAMA_API_KEY="la_teva_clau"
```

Comprovar que la variable existeix sense imprimir el secret:

```powershell
if (Test-Path Env:OLLAMA_API_KEY) { "OLLAMA_API_KEY=set" } else { "OLLAMA_API_KEY=missing" }
```

## Pas 1: prova curta de connexio

Executa una tasca per validar endpoint, clau i nom del model:

```powershell
python ml/rag/scripts/evaluate_ollama_models.py --base-url https://ollama.com --models qwen3-coder-next --limit 1 --timeout 180
```

Resultat esperat:

- `successful_tasks = 1`;
- `errors = 0`;
- resposta no buida a `ml/rag/results/ollama_manual_review.csv`.

## Pas 2: execucio completa d'un model

Quan la prova curta funcioni:

```powershell
python ml/rag/scripts/evaluate_ollama_models.py --base-url https://ollama.com --models qwen3-coder-next --timeout 180
```

Aquesta execucio ha de generar:

- `ml/rag/results/ollama_model_comparison.json`;
- `ml/rag/results/ollama_model_comparison.csv`;
- `ml/rag/results/ollama_manual_review.csv`.

## Pas 3: comparar contra baselines

Comparativa recomanada:

```powershell
python ml/rag/scripts/evaluate_ollama_models.py --base-url https://ollama.com --models qwen3-coder-next --timeout 180
python ml/rag/scripts/evaluate_ollama_models.py --models gemma3:1b --timeout 180
python ml/rag/scripts/evaluate_ollama_models.py --models gemma4:latest --limit 2 --timeout 300
python ml/rag/scripts/evaluate_ollama_models.py --models qwen3.6:latest --limit 2 --timeout 600
```

Notes:

- `gemma3:1b` es el baseline local petit.
- `gemma4:latest` i `qwen3.6:latest` son comparadors locals pesats; si retornen respostes buides o latencies extremes, es poden documentar com a no aptes en aquest hardware.
- Si es vol una taula unica, cal executar els models en una mateixa comanda nomes quan comparteixin el mateix `base-url`.

## Pas 4: revisio manual

Obre:

```text
ml/rag/results/ollama_manual_review.csv
```

Omple per cada resposta:

| Camp | Escala | Criteri |
| --- | --- | --- |
| `catalan_quality_1_5` | 1-5 | Naturalitat, correccio i claredat en catala. |
| `respects_rag_context_1_5` | 1-5 | Usa els documents recuperats sense sortir-se del context. |
| `invented_data_1_5` | 1-5 | 1 vol dir cap invencio; 5 vol dir invencio greu. |
| `recommendation_usefulness_1_5` | 1-5 | Recomanacio accionable i prudent per a l'usuari. |

Regla practica:

- un model candidat hauria de tenir `invented_data_1_5 <= 2`;
- hauria de tenir `respects_rag_context_1_5 >= 4`;
- hauria de mantenir una latencia assumible per a la pantalla de detall.

## Pas 5: informe final

Actualitza `ml/rag/results/model_selection_report.md` amb:

- data de l'experiment;
- models provats;
- comandes executades;
- taula de metriques automatiques;
- resum de la revisio manual;
- decisio final i justificacio;
- limitacions detectades.

Plantilla de decisio:

```text
Model seleccionat: <model>

Motiu:
- millor equilibri entre qualitat, latencia i grounding;
- respostes en catala estables;
- no inventa metriques ni dades mediques;
- compatible amb fallback local quan Ollama no respon.

Limitacions:
- depen de connexio externa si s'usa cloud;
- el corpus RAG encara es petit;
- caldria validar amb mes sessions reals.
```

## Pas 6: validacio Android

Amb el model candidat:

1. Obrir l'app.
2. Anar a `Perfil`.
3. Activar `Usar Ollama per als resums`.
4. Posar `https://ollama.com` com a Base URL si es cloud.
5. Posar el model exacte, per exemple `qwen3-coder-next`.
6. Afegir la API key si cal.
7. Guardar.
8. Crear i finalitzar una sessio.
9. Obrir el detall de sessio.
10. Confirmar que es mostra proveidor `ollama`, model, latencia i resposta.
11. Repetir amb endpoint desconnectat per validar fallback `rules`.

## Criteri d'acceptacio

La integracio IA es pot considerar tancada per al prototip si:

- el RAG local funciona offline;
- Ollama genera resposta quan esta configurat;
- el fallback local es guarda si Ollama falla;
- la pantalla mostra proveidor, model, latencia i estat de fallback;
- hi ha resultats experimentals versionats;
- hi ha revisio manual de qualitat;
- la documentacio explica reproduccio, limits i decisio final.
