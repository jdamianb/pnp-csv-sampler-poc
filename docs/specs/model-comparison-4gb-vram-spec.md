# Specification: Model Comparison for 4 GB VRAM

## 1. Purpose

Update documentation and scripts so the user can benchmark small Ollama models (3B-4B) using the existing Stage 7 evaluation harness. Primary candidate: `qwen2.5-coder:3b`. Comparison: `llama3.2:3b`, `phi4-mini`, `gemma3:4b`. No fine-tuning.

## 2. Scope

**In scope:**
- New comparison script `scripts/evaluate-ollama-models.sh`
- Documentation updates (README, Stage 7 docs, AGENTS.md)
- IntelliJ configurations for comparison runs
- Model selection via `PNP_LLM_MODEL` environment variable (already supported)

**Out of scope:**
- Fine-tuning
- RAG, UI, database
- 7B/8B models as default
- Changes to Java source code
- Changes to deterministic tests
- Changes to parser behavior
- Requiring cloud provider

## 3. EARS Requirements

### 3.1 Ubiquitous

| ID | Requirement |
|---|---|
| R01 | The system SHALL support model selection via `PNP_LLM_MODEL` environment variable |
| R02 | The evaluation harness SHALL work with any Ollama model set via `PNP_LLM_MODEL` |
| R03 | The default stub behavior SHALL remain unchanged when no Ollama is configured |

### 3.2 Event-driven

| ID | Requirement |
|---|---|
| R04 | WHEN `PNP_LLM_MODEL` is set, the evaluation SHALL use that model instead of the default |
| R05 | WHEN running the comparison script, a failure in one model SHALL NOT stop the remaining models |

### 3.3 Feature

| ID | Requirement |
|---|---|
| R06 | A comparison script `scripts/evaluate-ollama-models.sh` SHALL evaluate 4 models in sequence |
| R07 | The script SHALL save per-model reports to `target/evaluation-reports/evaluation-<model>.json` |
| R08 | The script SHALL print a compact summary of report paths after completion |
| R09 | The script SHALL set `PNP_LLM_PROVIDER=ollama`, `PNP_LLM_BASE_URL=http://localhost:11434`, `PNP_LLM_TEMPERATURE=0` for each run |
| R10 | The project SHALL document `qwen2.5-coder:3b` as the first recommended model for 4 GB VRAM |
| R11 | The project SHALL document `llama3.2:3b`, `phi4-mini`, `gemma3:4b` as comparison candidates |
| R12 | The project SHALL document the `ollama pull` commands for all 4 candidate models |
| R13 | Existing deterministic tests SHALL pass without Ollama or network access |

## 4. Comparison Script Specification

### Location: `scripts/evaluate-ollama-models.sh`

### Models (in order):
1. `qwen2.5-coder:3b`
2. `llama3.2:3b`
3. `phi4-mini`
4. `gemma3:4b`

### Behavior:
1. Check Ollama connectivity before starting
2. For each model:
   - Set `PNP_LLM_MODEL` environment variable
   - Run `scripts/run-evaluation.sh http://localhost:11434 <model>`
   - Copy evaluation report to `target/evaluation-reports/evaluation-<model-sanitized>.json`
   - Continue to next model on failure
3. Print compact summary of all report paths

### Output files:
```
target/evaluation-reports/evaluation-qwen2.5-coder-3b.json
target/evaluation-reports/evaluation-llama3.2-3b.json
target/evaluation-reports/evaluation-phi4-mini.json
target/evaluation-reports/evaluation-gemma3-4b.json
```

## 5. IntelliJ Configurations

| Configuration | Command |
|---|---|
| `Run all model comparisons` | `scripts/evaluate-ollama-models.sh` |
| `Run evaluation (Ollama qwen2.5-coder:3b)` | `scripts/run-evaluation.sh http://localhost:11434 qwen2.5-coder:3b` |

## 6. Human Review Checklist

- [ ] Comparison script exists and runs 4 models in sequence
- [ ] Per-model reports saved to `target/evaluation-reports/`
- [ ] Models continue on individual failures
- [ ] `mvn test` passes without Ollama
- [ ] README documents `qwen2.5-coder:3b` as primary recommendation
- [ ] `ollama pull` commands documented
- [ ] No fine-tuning code added
- [ ] No 7B/8B model becomes default
- [ ] Stub behavior unchanged
