# Feature Request: Stage 3 Real LLM Adapter

## Context

Stage 1 implemented the CSV sampler.

Stage 2 implemented the format detection contract, prompt builder, stub LLM client, config DTOs, and validator.

The detector currently uses `StubLlmClient`, which returns a hardcoded config without analyzing the actual file.

## Goal

Add a real LLM adapter behind the existing `LlmClient` abstraction so that the detector can propose a `PnpImportFormatConfig` that actually matches the file content.

The first preferred adapter is local Ollama.

The default behavior must remain stubbed and deterministic.

## Scope

- Add provider configuration (CLI args and/or environment variables)
- Add `OllamaLlmClient`
- Add `LlmClientFactory` to create the right client based on configuration
- Update `detect` command to allow provider selection (`--llm-provider`, `--llm-url`, `--llm-model`)
- Keep `StubLlmClient` as default when no provider is configured
- Parse real LLM output as `PnpImportFormatConfig` using the existing Jackson pipeline
- Validate real LLM output with the existing `PnpImportFormatConfigValidator`
- Handle connection failures, non-JSON responses, and invalid configs with clear errors
- Add offline unit tests
- Add optional opt-in integration test support

## Out of scope

- parser dry-run (Stage 4)
- repair loop (Stage 5)
- UI (Stage 6)
- database
- RAG
- fine-tuning
- replacing the existing parser
- component-row normalization by the LLM
- production persistence of detected formats
- OpenAI-compatible adapter (deferred unless explicitly requested)

## Acceptance criteria

- `mvn test` passes offline
- Default `detect` command still works with `StubLlmClient`
- Ollama provider is opt-in via CLI arguments or environment variables
- Missing Ollama configuration fails with clear error
- Ollama connection failures fail with clear error
- Non-JSON model responses fail with clear error
- Invalid `PnpImportFormatConfig` responses fail validation
- Valid LLM responses are parsed and printed as pretty JSON
- No API keys or secrets are committed
- Real LLM tests are opt-in only
- README documents stub and real LLM usage
