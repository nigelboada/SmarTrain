# Comparativa de models generatius per SmarTrain

Data: 2026-05-14 18:40:25

## Metodologia

Tots els models s'han avaluat amb les mateixes 10 tasques del RAG: 6 preguntes documentals i 4 sessions sintetiques. El retrieval es mante constant amb TF-IDF i `top_k=3`; per tant, la comparacio mesura principalment la generacio.

Les metriques qualitatives son automatices i orientatives: combinen solapament amb el context recuperat, cobertura de termes esperats, marcadors de catala, deteccio de xifres no suportades i presencia de recomanacions accionables. No substitueixen una auditoria humana, pero eviten haver d'omplir un CSV manual per al prototip.

## Resultats resum

| Proveidor | Model | Tasques OK | Errors/timeouts | Latencia mitjana | P95 latencia | Catala | Respecte RAG | No invencio | Utilitat | Grounded overlap |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| cloud | `gemma3:4b` | 10/10 | 0 | 702.98 ms | 977.94 ms | 2.516 | 4.2 | 5.0 | 3.301 | 0.733 |
| local | `gemma4:latest` | 10/10 | 0 | 43190.86 ms | 96556.1 ms | 3.003 | 3.982 | 5.0 | 3.873 | 0.661 |
| cloud | `qwen3-coder-next` | 10/10 | 0 | 3378.32 ms | 12119.02 ms | 3.33 | 3.882 | 4.94 | 3.735 | 0.627 |
| local | `gemma3:1b` | 10/10 | 0 | 8022.97 ms | 11958.88 ms | 2.586 | 3.597 | 4.84 | 3.43 | 0.532 |
| local | `qwen3.6:latest` | 9/10 | 1 | 63571.25 ms | 119281.17 ms | 3.07 | 3.71 | 5.0 | 3.81 | 0.57 |
| cloud | `gpt-oss:20b` | 8/10 | 2 | 1554.34 ms | 2191.71 ms | 2.781 | 3.596 | 4.825 | 3.599 | 0.532 |

## Exemples de resposta

### cloud - `gemma3:4b`

Tasca: `q_001`

> Si la sessió té molta alta intensitat, es recomana alternar blocs d'alta intensitat amb recuperacions suficients.

### local - `gemma4:latest`

Tasca: `q_001`

> Si la sessió té alta intensitat, pot contenir canvis de ritme, acceleracions o moviments exigents. Es recomana alternar aquests blocs amb recuperacions suficients. Recorda fer recuperació, hidratació i analitzar la càrrega després de sessió.

### cloud - `qwen3-coder-next`

Tasca: `q_001`

> Indica que la sessió inclou molts canvis de ritme, acceleracions o moviments exigents (com pujar o baixar escales). Es recomana alternar aquests blocs amb recuperacions suficients i, si és la segona sessió intensa seguida, la propera pot prioritzar tecnica, mobilitat o treball aerobi suau.

### local - `gemma3:1b`

Tasca: `q_001`

> Si la sessió té molta intensitat, és possible que s'utilitzin aproximacions tècniques per a accions que requereixen un moviment o un ritme intens. Recomanem alternar aquests blocs amb recuperacions suficients per evitar canvis de ritme.

### local - `qwen3.6:latest`

Tasca: `s_004`

> La sessió s'ha caracteritzat per alta intensitat amb una confiança moderada (48%). Atès que 7 de 16 blocs són d'alta intensitat, es recomana prioritzar la recuperació, la hidratació i l'anàlisi de la càrrega. Per a la propera sessió, es suggereix treballar la tècnica, la mobilitat o un treball aerobi suau per equilibrar l'estrès acumulat.

### cloud - `gpt-oss:20b`

Tasca: `q_001`

> Una sessió amb molta alta intensitat indica que la major part de l’entrenament es va dedicar a blocs exigents (p. ex. pujar/baixar escales, acceleracions, moviments intensos).   **Recomanació accionable:** inclou una fase de recuperació activa, hidrata’t adequadament i analitza la càrrega. Si la sessió ha estat massa intensa, la propera pot prioritzar tècnica, mobilitat o treball aeròbi suau per equilibrar la càrrega.

## Decisio final

Model recomanat: **`qwen3-coder-next` (cloud)**.

La decisio es basa en l'equilibri entre 10/10 tasques correctes, latencia, respecte del context RAG, baixa invencio de dades i utilitat de la recomanacio.
Com que es cloud, s'ha de mantenir el fallback local per garantir que l'app continua funcionant sense connexio o si la API falla.
Els models amb latencia mitjana superior a 10 segons no es consideren recomanables com a opcio principal de l'app, encara que puguin tenir bones puntuacions qualitatives.

## Fitxers generats

- `C:/Users/nigel/Documents/plataformes/SmarTrain/ml/rag/results/generation_model_comparison.json`
- `C:/Users/nigel/Documents/plataformes/SmarTrain/ml/rag/results/generation_model_comparison.csv`
- `C:/Users/nigel/Documents/plataformes/SmarTrain/ml/rag/results/generation_model_comparison_report.md`
