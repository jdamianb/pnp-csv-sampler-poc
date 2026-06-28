# Handoff: Stage 5 Leader to Spec Writer

## Context

Stages 1-4 are complete. The project now has:
- Stage 1: CSV Sampler
- Stage 2: Format Detection contract (LlmClient, validator, stub)
- Stage 3: Ollama Real LLM Adapter
- Stage 4: Simple Open PnP Parser (deterministic dry-run)

Stage 5 adds a **bounded repair loop** that automatically corrects invalid `PnpImportFormatConfig` proposals by sending validation and parser errors back to the LLM.

## Inputs

- `AGENTS.md` — Stage 5 definition (bounded repair, max 2-3 retries, repair prompt with errors)
- `docs/feature-requests/stage5-repair-loop.md` — detailed feature request
- `src/main/java/com/example/pnp/Detector.java` — existing orchestrator
- `src/main/java/com/example/pnp/FormatDetectionPromptBuilder.java` — existing prompt builder
- `src/main/java/com/example/pnp/PnpImportFormatConfigValidator.java` — existing validator
- `src/main/java/com/example/pnp/SimplePnpParser.java` — existing parser
- `src/main/java/com/example/pnp/LlmClient.java` — existing LLM interface

## Expected outputs

Create `docs/specs/stage5-repair-loop-spec.md` containing:

1. **Purpose** — bounded repair loop that corrects invalid configs
2. **Scope** — repair prompt builder, repair orchestrator, repaired CLI output
3. **Out of scope** — UI, persistence, fine-tuning, section-aware repair
4. **Assumptions** — LLM can correct its own config when given specific errors
5. **EARS requirements**
6. **Gherkin acceptance scenarios**
7. **Non-functional requirements**
8. **Testability notes**
9. **Human review checklist**

## Key Design Points

- **Bounded**: max retries configurable (default: 2), stop when config is valid
- **Repair prompt**: contains original CSV sample + broken config + validation/parser errors
- **Stub handling**: StubLlmClient always returns the same JSON → repair is futile after 1 attempt
- **CLI flags**: `--repair-max N` (default: 2), `--no-repair` (disable)
- **No data repair**: only config fields are corrected, never component data
- **Expose failure**: when max retries exceeded, show errors clearly (don't hide them)

## Stop condition

After writing the spec, return control to the leader.

The leader will stop for human approval before implementation begins.
