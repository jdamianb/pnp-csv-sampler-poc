# Handoff: Stage 3 Leader to Spec Writer

## Context

Stage 1 (CSV Sampler) and Stage 2 (LLM-assisted Format Detection contract) are complete.

The project now has:
- A CSV sampler that produces numbered raw samples
- A `PnpImportFormatConfig` model with 14 fields
- A `FormatDetectionPromptBuilder` that builds LLM prompts from samples
- An `LlmClient` interface with a deterministic `StubLlmClient`
- A `PnpImportFormatConfigValidator` with 12 validation rules
- A `Detector` orchestrator (prompt → LLM → parse → validate)
- A `detect <file>` CLI command that currently uses `StubLlmClient`

Stage 3 introduces a real LLM adapter behind the existing `LlmClient` interface.

The first preferred adapter is **Ollama** (local).

The default behavior must remain stubbed and deterministic.

## Inputs

- `AGENTS.md` — Stages 1-8 documented
- `.goose/roles/leader.md` — leader role definition
- `.goose/roles/spec-writer.md` — spec writer role definition
- `docs/feature-requests/stage3-real-llm-adapter.md` — Stage 3 feature request
- `docs/specs/llm-assisted-pnp-format-detection-spec.md` — Stage 2 spec (do not modify)
- `src/main/java/com/example/pnp/LlmClient.java` — existing interface to reuse
- `prompt.md` — detailed Stage 3 implementation guidance

## Expected outputs

Create `docs/specs/stage3-real-llm-adapter-spec.md` containing:

1. **Purpose** — add real LLM adapter behind existing interface
2. **Scope** — Ollama client, provider config, factory, CLI flags, opt-in integration tests
3. **Out of scope** — parser dry-run, repair loop, UI, database, RAG, fine-tuning
4. **Assumptions** — Stage 2 pipeline exists, StubLlmClient is default, Ollama is local
5. **EARS requirements** — using ubiquitous, event-driven, state-driven, and unwanted behavior patterns
6. **Gherkin acceptance scenarios** — covering stub default, Ollama opt-in, connection errors, parsing errors, validation errors
7. **Non-functional requirements** — offline tests, opt-in integration tests, no secrets, temperature=0
8. **Testability notes** — stub remains default, integration tests behind flag
9. **Human review checklist**

## Constraints

- **StubLlmClient** must remain the default when no provider is configured
- **Real LLM calls** must be opt-in via CLI args or environment variables
- **No secrets** committed (API keys, tokens, private URLs)
- **Unit tests** must pass offline without network, API keys, or local LLM
- **Real LLM tests** must be opt-in integration tests
- **No Spring Boot**, no database, no UI, no RAG, no fine-tuning
- **Existing pipeline** (prompt builder, validator, detector) must remain unchanged
- **Temperature** should default to `0` for deterministic detection

## Stop condition

After writing the spec, return control to the leader.

The leader will stop for human approval before implementation begins.
