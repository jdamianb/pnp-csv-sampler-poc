# Handoff: Stage 3 Implementer to Reviewer

## Context

Stage 3 (Real LLM Adapter) implementation is complete following the approved specification at `docs/specs/stage3-real-llm-adapter-spec.md`.

The implementation adds a real LLM adapter behind the existing `LlmClient` interface, targeting local Ollama. The default behavior remains stubbed and deterministic.

## Implementation Summary

| Aspect | Detail |
|---|---|
| Language | Java 21 |
| Build tool | Maven 3.8.7 |
| Dependencies | Java built-in `HttpClient` — no new dependencies |
| Tests | 109 total (77 existing + 32 new), 3 skipped (integration) |
| Spring Boot | **Not used** |
| Real LLM | Opt-in via CLI args or environment variables |

## Source Files Added

| File | Description |
|---|---|
| `src/main/java/com/example/pnp/LlmOptions.java` | Record: provider, baseUrl, model, temperature |
| `src/main/java/com/example/pnp/OllamaLlmClient.java` | Real LLM client using Java `HttpClient` + Ollama `/api/generate` |
| `src/main/java/com/example/pnp/LlmException.java` | RuntimeException for LLM errors |
| `src/main/java/com/example/pnp/LlmClientFactory.java` | Factory: stub for null/blank provider, `OllamaLlmClient` for "ollama" |

## Source Files Modified

| File | Change |
|---|---|
| `src/main/java/com/example/pnp/Main.java` | Added `--llm-provider`, `--llm-url`, `--llm-model`, `--llm-temperature` flags + env var support + `LlmClientFactory` integration |

## Test Files Added

| File | Tests | Scope |
|---|---|---|
| `LlmOptionsTest.java` | 8 | Validation: null/blank provider, temperature range, record components |
| `LlmClientFactoryTest.java` | 7 | Factory: null/blank/ollama/unknown provider, default stub |
| `OllamaLlmClientTest.java` | 6 | Offline error paths: blank URL/model, connection refused, bad DNS |
| `OllamaIntegrationTest.java` | 3 | Opt-in (`-Dllm.integration=true`): real Ollama interaction |

## Test Files Modified

| File | Change |
|---|---|
| `MainTest.java` | Added 8 tests for `--llm-*` CLI flags |

## Architecture

```
CLI: detect <file> [--llm-provider ...] [--llm-url ...] [--llm-model ...]
  │
  ├── Sampler → SampleResult
  ├── FormatDetectionPromptBuilder → prompt
  ├── LlmClientFactory.create(LlmOptions)
  │     ├── provider=null/blank → StubLlmClient (default)
  │     └── provider="ollama" → OllamaLlmClient
  ├── OllamaLlmClient
  │     ├── POST /api/generate {model, prompt, stream:false, options:{temperature}}
  │     └── Extracts "response" field from Ollama JSON
  ├── Detector → parse → validate
  └── Output: config JSON or errors
```

## CLI Flags (detect mode only)

| Flag | Env Variable | Default | Description |
|---|---|---|---|
| `--llm-provider NAME` | `PNP_LLM_PROVIDER` | (stub) | Provider: "ollama" |
| `--llm-url URL` | `PNP_LLM_BASE_URL` | — | Endpoint URL |
| `--llm-model MODEL` | `PNP_LLM_MODEL` | — | Model name |
| `--llm-temperature T` | `PNP_LLM_TEMPERATURE` | 0 | Temperature |

CLI args take precedence over environment variables.

## Error Handling

| Scenario | Behavior |
|---|---|
| No provider configured | StubLlmClient, exit 0 |
| `--llm-provider` set to unknown | Error message, exit 1 |
| `--llm-url` missing when provider set | Error from constructor, exit 1 |
| `--llm-model` missing when provider set | Error from constructor, exit 1 |
| Ollama not running (connection refused) | `LlmException` with clear message, exit 1 |
| Non-JSON response from Ollama | `LlmException` with parse error, exit 1 |
| Invalid config from Ollama | Validation errors, exit 1 |

## Verification

```bash
# Default (stub)
mvn test                                          # 109 tests, 3 skipped
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv

# With Ollama
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv \
  --llm-provider ollama \
  --llm-url http://localhost:11434 \
  --llm-model qwen2.5:7b

# Environment variables
PNP_LLM_PROVIDER=ollama PNP_LLM_BASE_URL=http://localhost:11434 \
PNP_LLM_MODEL=qwen2.5:7b \
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv

# Integration tests
mvn test -Dllm.integration=true
```

## Scope Compliance

**In scope (all implemented):**
- [x] `LlmOptions` record with provider config
- [x] `OllamaLlmClient` using Java `HttpClient`
- [x] `LlmClientFactory` — creates stub or real client
- [x] Provider selection via CLI args and environment variables
- [x] `StubLlmClient` remains default
- [x] Error handling for all failure scenarios
- [x] Offline unit tests
- [x] Opt-in integration tests (`-Dllm.integration=true`)

**Out of scope (not implemented):**
- [x] No parser dry-run (Stage 4)
- [x] No repair loop (Stage 5)
- [x] No UI (Stage 6)
- [x] No database
- [x] No RAG or fine-tuning
- [x] No parser replacement
- [x] No secrets committed

## Reviewer Checklist

- [ ] Code quality — clean, well-structured Java records and classes
- [ ] Scope compliance — no out-of-scope features introduced
- [ ] All 109 tests pass (3 skipped = integration)
- [ ] Default `detect` still uses StubLlmClient
- [ ] Ollama provider is opt-in
- [ ] Error handling covers all failure paths
- [ ] No secrets or API keys committed
- [ ] No Spring Boot
- [ ] No new dependencies (uses Java built-in HttpClient)
- [ ] Temperature defaults to 0
