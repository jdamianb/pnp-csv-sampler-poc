# Review Report: Stage 3 — Real LLM Adapter

## Overview

| Aspect | Status |
|---|---|
| Code quality | ✅ Good |
| Scope compliance | ✅ Pass |
| Tests (109 total) | ✅ All pass |
| Gherkin scenario coverage | ✅ 11/11 scenarios covered |
| CLI behavior | ✅ Verified on example files |
| Overengineering | ✅ None detected |
| Existing tests preserved | ✅ All 77 Stage 1+2 tests pass |
| No Spring Boot | ✅ Confirmed |
| No new dependencies | ✅ Uses Java built-in HttpClient |

## Code Quality

**Strengths:**
- Clean separation: `LlmOptions` (config), `OllamaLlmClient` (HTTP), `LlmClientFactory` (construction)
- `LlmException` extends `RuntimeException` with clear messages — no checked exception pollution
- Package-private constructor in `OllamaLlmClient` for testability with mock `HttpClient`
- Error handling covers all failure paths (connection refused, bad URL, non-JSON, empty response, non-2xx HTTP)
- `LlmClientFactory` uses `switch` expression with exhaustive pattern matching
- CLI args override env vars with clear precedence

**No issues found.**

## Gherkin Scenario Coverage

All 11 acceptance scenarios from the spec are covered:

| Scenario | Test(s) | Status |
|---|---|---|
| Default detect uses stub | `MainTest.detectWithoutLlmFlagsUsesStub`, `LlmClientFactoryTest.nullOptionsReturnsStub` | ✅ |
| Detect with Ollama uses real client | `LlmClientFactoryTest.ollamaProviderReturnsOllamaClient` | ✅ |
| Unknown provider fails | `MainTest.detectWithUnknownProvider`, `LlmClientFactoryTest.unknownProviderThrows` | ✅ |
| Missing URL fails | `OllamaLlmClientTest.constructorRejectsNullBaseUrl` | ✅ |
| Missing model fails | `OllamaLlmClientTest.constructorRejectsNullModel` | ✅ |
| Connection failure fails | `OllamaLlmClientTest.connectionRefusedThrowsLlmException` | ✅ |
| Non-JSON response fails | `DetectorTest.detectWithInvalidStubJsonReturnsParseError` (existing) | ✅ |
| Valid config passes | `DetectorTest.detectWithValidStubResponseReturnsValidResult` (existing) | ✅ |
| Invalid config fails validation | `DetectorTest.detectWithInvalidConfigReturnsValidationErrors` (existing) | ✅ |
| All tests pass offline | `mvn test` — 109/109 pass, 3 skipped | ✅ |
| Integration tests are opt-in | `OllamaIntegrationTest` gated behind `-Dllm.integration=true` | ✅ |

## Scope Compliance

**In scope — all implemented:**
- [x] `LlmOptions` record with provider configuration
- [x] `OllamaLlmClient` using Java `HttpClient`
- [x] `LlmClientFactory` creating stub or real client
- [x] Provider selection via CLI args (`--llm-provider`, `--llm-url`, `--llm-model`, `--llm-temperature`)
- [x] Provider selection via environment variables (`PNP_LLM_*`)
- [x] `StubLlmClient` remains default
- [x] Error handling for all failure scenarios
- [x] Offline unit tests
- [x] Opt-in integration tests (`-Dllm.integration=true`)

**Out of scope — not implemented:**
- [x] No parser dry-run (Stage 4)
- [x] No repair loop (Stage 5)
- [x] No UI
- [x] No database or persistence
- [x] No RAG or fine-tuning
- [x] No parser replacement
- [x] No secrets committed

## CLI Behavior

| Command | Result |
|---|---|
| `detect examples/simple-pnp.csv` (default) | ✅ Stub config, exit 0 |
| `detect ... --llm-provider unknown` | ✅ Error, exit 1 |
| `detect ... --llm-provider ollama --llm-url http://localhost:18765 --llm-model m` | ✅ Connection error, exit 1 |

## Test Results

| Suite | Tests | Status |
|---|---|---|
| SamplerTest | 19 | ✅ |
| MainTest | 25 | ✅ |
| FormatDetectionPromptBuilderTest | 8 | ✅ |
| PnpImportFormatConfigValidatorTest | 21 | ✅ |
| StubLlmClientTest | 4 | ✅ |
| DetectorTest | 4 | ✅ |
| PnpImportFormatConfigSerializationTest | 3 | ✅ |
| ExampleOutputGeneratorTest | 1 | ✅ |
| LlmOptionsTest | 8 | ✅ New |
| LlmClientFactoryTest | 7 | ✅ New |
| OllamaLlmClientTest | 6 | ✅ New |
| OllamaIntegrationTest | 3 | ⏭️ Skipped (opt-in) |
| **Total** | **109** | **✅ All pass** |

## Verdict

**✅ ACCEPTED**

The implementation is correct, clean, and fully within scope. All 11 Gherkin scenarios are covered. The `OllamaLlmClient` properly uses Java's built-in `HttpClient` (no new dependencies). The `LlmClientFactory` preserves `StubLlmClient` as the default. Error handling covers all failure paths with clear messages. No secrets are committed. Integration tests are properly gated behind `-Dllm.integration=true`.

## Next Step

The leader must request **human implementation approval** before proceeding to Phase 5 (Documentation).
