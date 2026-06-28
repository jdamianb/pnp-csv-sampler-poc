# Handoff: Model Comparison for 4 GB VRAM — Leader to Spec Writer

## Context

The separator detection improvement is complete, but qwen2.5:3b still does not reliably fix format detection for non-comma delimiters.

Goal: update documentation and scripts so the user can benchmark small Ollama models (3B-4B) easily on a laptop with 4 GB VRAM.

## Inputs

- `prompt-model-comparison-4gb-vram.md` at project root — detailed requirements
- Existing `scripts/run-evaluation.sh` — evaluation harness to reuse
- Existing `scripts/run-repair-loop.sh` — repair loop runner (not needed here)
- Existing `.run/` IntelliJ configurations

## Expected Outputs

1. **Script**: `scripts/evaluate-ollama-models.sh` — evaluates 4 models, saves per-model reports
2. **Docs**: Update `docs/features/stage7-evaluation.md` — add model comparison section
3. **Docs**: Update `docs/testing/stage7-evaluation.md` — add model testing instructions
4. **README**: Update with model comparison instructions and `qwen2.5-coder:3b` as primary recommendation
5. **AGENTS.md**: Add documentation reference for the model comparison

## Constraints

- Primary model: `qwen2.5-coder:3b`
- Comparison models: `llama3.2:3b`, `phi4-mini`, `gemma3:4b`
- No 7B/8B models as default
- No fine-tuning
- No RAG, UI, or database
- Deterministic tests must remain offline
- Default stub behavior must still work without Ollama
- Must support model selection via `PNP_LLM_MODEL` env var or CLI arg

## Next

Spec writer creates a short spec with EARS requirements and stops for human approval.
