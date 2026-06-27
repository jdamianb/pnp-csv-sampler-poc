# Stage 2 Workflow: LLM-assisted PnP Format Detection

This workflow extends the existing PoC harness after the CSV sampler is complete.

## Phase 0: Leader intake

Leader reads:

- `AGENTS.md`
- `.goose/roles/leader.md`
- `docs/feature-requests/llm-assisted-pnp-format-detection.md`

Leader confirms that Stage 1 sampler is complete.

Leader creates:

`.goose/handoffs/stage2-leader-to-spec-writer.md`

## Phase 1: Specification

Spec writer creates:

`docs/specs/llm-assisted-pnp-format-detection-spec.md`

The spec must include:

- EARS requirements
- Gherkin scenarios
- config schema expectations
- validation rules
- explicit out-of-scope list
- human review checklist

The spec must emphasize:

- the LLM proposes configuration only
- the LLM does not parse components
- the existing parser remains the authority
- the first implementation uses `StubLlmClient`

Leader stops for human approval.

## Phase 2: Stubbed detector implementation

After human spec approval, implementer adds:

- `PnpImportFormatConfig`
- `ColumnMapping`
- `ColumnSource`
- `FormatDetectionPromptBuilder`
- `PnpImportFormatConfigValidator`
- `LlmClient`
- `StubLlmClient`
- `detect <file>` CLI command
- unit tests

The implementer must not add real network calls in this phase.

Required command:

```bash
mvn test
```

Implementer creates:

`.goose/handoffs/stage2-implementer-to-reviewer.md`

## Phase 3: Review loop

Reviewer checks:

- scope compliance
- tests
- validation behavior
- prompt quality
- no real LLM dependency in tests
- no parser replacement
- no Spring Boot

If changes are required:

```text
Reviewer -> Implementer -> Reviewer
```

The loop continues until:

- tests pass
- reviewer accepts
- scope is respected

Reviewer creates:

`.goose/reviews/stage2-format-detector-review.md`

Leader stops for human implementation approval.

## Phase 4: Documentation

After human implementation approval, doc writer updates:

- `docs/features/llm-assisted-pnp-format-detection.md`
- `docs/testing/llm-assisted-pnp-format-detection-tests.md`
- `README.md`
- `AGENTS.md`, only if the workflow changed

Doc writer creates:

`.goose/handoffs/stage2-doc-writer-completion.md`

Leader stops for final human review.

## Phase 5: Optional real LLM adapter

This phase is not automatic.

Only start this phase after explicit human approval.

Possible next feature requests:

- Ollama local LLM adapter
- OpenAI-compatible local LLM adapter
- DeepSeek cloud fallback adapter
- parser dry-run feedback loop
