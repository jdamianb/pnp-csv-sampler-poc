# Handoff: Stage 2 Leader to Spec Writer

## Context

Stage 1 (CSV Sampler) is complete. The sampler reads a PnP CSV/TSV-like file and produces a numbered raw sample JSON.

Stage 2 introduces an LLM-assisted format detection contract. The goal is to let an LLM propose the manual PnP import configuration that users currently enter by hand — without parsing the file or replacing the existing parser.

The first Stage 2 implementation must use `StubLlmClient` for deterministic tests. No real LLM calls yet.

## Inputs

- `AGENTS.md` — already contains Stages 1–8 documented
- `.goose/roles/spec-writer.md` — spec writer role definition
- `.goose/roles/leader.md` — leader role definition
- `.goose/workflows/stage2-format-detector-workflow.md` — Stage 2 workflow
- `.goose/templates/stage2-format-detector-spec-template.md` — Stage 2 spec template
- `docs/feature-requests/llm-assisted-pnp-format-detection.md` — Stage 2 feature request
- `docs/specs/pnp-csv-sampler-spec.md` — existing Stage 1 spec (do not modify)

## Expected outputs

Create `docs/specs/llm-assisted-pnp-format-detection-spec.md` containing:

1. **Purpose** — reduce manual PnP import configuration
2. **Scope** — what the feature includes (config model, prompt builder, validator, stub LLM, CLI)
3. **Out of scope** — no Spring Boot, no real LLM calls, no parser replacement, no database, no UI
4. **Assumptions** — sampler exists, parser exists, LLM is a config assistant
5. **EARS requirements** — using ubiquitous, event-driven, state-driven, and unwanted behavior patterns
6. **Gherkin acceptance scenarios** — covering all validation rules and prompt behavior
7. **Config schema** — `PnpImportFormatConfig` with `ColumnMapping`, `ColumnSource`, validation rules
8. **Non-functional requirements** — deterministic tests, no network, interface for LLM
9. **Testability notes** — prompt builder, config parsing, validator, stub LLM, CLI
10. **Human review checklist**

## Constraints

- The **existing CSV parser** remains the authority. The LLM must not parse the file.
- The LLM must only **propose** the parser configuration (delimiter, columns, units, etc.)
- The **first implementation** must use `StubLlmClient` — no real LLM calls
- The **sample command** must continue to work unchanged
- All **existing tests** must still pass
- No Spring Boot, no database, no UI, no RAG, no fine-tuning

## Allowed classes for Stage 2

- `PnpImportFormatConfig`
- `ColumnMapping`
- `ColumnSource`
- `FormatDetectionPromptBuilder`
- `PnpImportFormatConfigValidator`
- `LlmClient` abstraction
- `StubLlmClient`
- `detect <file>` CLI command
- deterministic tests

## Stop condition

After writing the spec, return control to the leader.

The leader will stop for human approval before implementation begins.
