# Model selection report for SmarTrain RAG

Date: 2026-05-14

## Available Ollama models

Detected with `ollama list`:

| Model | Size | Notes |
| --- | ---: | --- |
| `gemma3:1b` | 815 MB | Small local baseline. Completed the full evaluation. |
| `gemma4:latest` | 9.6 GB | Larger local model. Very slow and returned empty responses in the current run. |
| `qwen3.6:latest` | 23 GB | Installed locally. The previous command used `qwen3.6`, which produced 404 because the API model name is `qwen3.6:latest`. A retry with the correct name returned empty responses after very high latency. |

## Run summary provided from Android Studio terminal

Command:

```powershell
python ml/rag/scripts/evaluate_ollama_models.py --models gemma3:1b qwen3.6 gemma4
```

Important naming note: `qwen3.6` was not a valid API model name in this environment. The valid name detected later is `qwen3.6:latest`.

| Model | Tasks | Successful tasks | Average latency | Errors/timeouts | Grounded overlap | Expected term coverage | Interpretation |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `gemma3:1b` | 10 | 10 | 10809.77 ms | 0 | 0.588 | 0.333 | Best current candidate: slow but usable, stable, non-empty answers. |
| `qwen3.6` | 10 | 0 | n/a | 10 | n/a | n/a | Invalid model name for the API; repeat as `qwen3.6:latest`. |
| `gemma4` | 10 | 9 in the old script, effectively poor | 85881.14 ms | 1 timeout plus many empty answers | 0.0 | 0.0 | Not suitable in current setup: too slow and low usable output. |

## Qualitative review

### `gemma3:1b`

Strengths:

- Responds in Catalan.
- Usually respects the retrieved RAG context.
- Gives short recommendations that are useful enough for the session detail screen.
- Does not appear to invent new numeric metrics in the inspected examples.

Weaknesses:

- Latency is around 8-18 seconds per answer in the current local environment.
- Catalan quality is acceptable but not polished; some phrasing is awkward.
- Term coverage on synthetic session tasks is modest, especially for low-confidence cases.

Example from `q_001`:

> If the session has high intensity, the model explains that it may represent demanding movement patterns and recommends alternating those blocks with enough recovery.

Decision impact:

- Good baseline and current safest candidate for app integration.
- Recommended for first real in-app IA test because it is stable and small.

### `gemma4:latest`

Strengths:

- Larger model, potentially better if correctly configured and given enough compute.

Weaknesses:

- Average latency was around 86 seconds.
- It produced empty responses for many tasks. The updated evaluator now counts empty responses as errors.
- Not acceptable for a mobile session summary flow in the current environment.

Decision impact:

- Do not select as the app default now.
- Keep as an experimental comparison point only.

### `qwen3.6:latest`

Current status:

- Installed and visible in `ollama list`.
- The initial run failed because the command used `qwen3.6` instead of `qwen3.6:latest`.
- A retry with the correct name on two tasks produced empty responses after about 93-98 seconds per task.
- `ollama ps` showed it running on CPU with a 26 GB footprint.

Decision impact:

- Not a practical candidate in the current local CPU setup.
- It can remain in the report as a larger-model comparison, but the current evidence is negative: very high latency and no usable answer.

Recommended retry:

```powershell
python ml/rag/scripts/evaluate_ollama_models.py --models qwen3.6:latest --limit 2 --timeout 600
```

If a future hardware/cloud setup makes that work, run the complete evaluation:

```powershell
python ml/rag/scripts/evaluate_ollama_models.py --models qwen3.6:latest --timeout 600
```

## Metrics used

Automatic metrics:

- `avg_latency_ms`: lower is better.
- `errors`: connection errors, timeouts, invalid model names and empty responses.
- `avg_grounded_overlap`: lexical overlap between answer and retrieved context. Higher is usually more grounded, but this is only a proxy.
- `avg_expected_term_coverage`: coverage of expected terms in synthetic session tasks.
- `avg_language_hint_score`: rough automatic signal that the answer contains Catalan/domain markers.
- `avg_recommendation_hint_score`: whether the answer includes recommendation-like wording.
- `avg_hallucination_risk_score`: inverse of grounded overlap; lower is better.

Manual metrics to fill in `ollama_manual_review.csv`:

- Catalan quality from 1 to 5.
- Respect for RAG context from 1 to 5.
- Invented data from 1 to 5, where 1 means no invented data and 5 means severe invention.
- Recommendation usefulness from 1 to 5.

## Current recommendation

Use `gemma3:1b` as the first IA model integrated in the app, with the existing rule-based fallback enabled.

Reason:

- It is the only model from the current complete run with 10/10 usable responses.
- It has acceptable grounding compared with the other measured candidates.
- Its latency is high but manageable for a first post-session summary experiment.
- It is small enough to run consistently on the current local Ollama setup.

Current practical decision for the local prototype: choose `gemma3:1b`.

The broader academic comparison should still include:

1. `gemma3:1b`
2. `qwen3.6:latest`
3. `gemma4:latest`, if a prompt/configuration fix stops empty responses
4. optionally one Ollama cloud model, if accessible through the API
