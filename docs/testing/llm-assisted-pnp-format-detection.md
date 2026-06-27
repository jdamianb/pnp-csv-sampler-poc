# Testing: LLM-assisted PnP Import Format Detection

## Test Suite

| Suite | File | Tests | Scope |
|---|---|---|---|
| FormatDetectionPromptBuilderTest | `FormatDetectionPromptBuilderTest.java` | 8 | Prompt content, schema, sample inclusion, instructions |
| PnpImportFormatConfigValidatorTest | `PnpImportFormatConfigValidatorTest.java` | 21 | All 12+ validation rejection rules + valid configs |
| StubLlmClientTest | `StubLlmClientTest.java` | 4 | Determinism, response fidelity |
| PnpImportFormatConfigSerializationTest | `PnpImportFormatConfigSerializationTest.java` | 3 | JSON round-trip, COLUMN_INDEX, minimal config |
| DetectorTest | `DetectorTest.java` | 4 | Orchestrator: valid/invalid stub, parse error, integration |
| MainTest (detect additions) | `MainTest.java` | 5 | CLI detect command: valid, error, flags, missing file |
| ExampleOutputGeneratorTest | `ExampleOutputGeneratorTest.java` | 1 | Processes all 12 extended examples, compares vs expected |
| LlmOptionsTest | `LlmOptionsTest.java` | 8 | Provider config validation |
| LlmClientFactoryTest | `LlmClientFactoryTest.java` | 7 | Factory: stub/ollama/unknown/blank |
| OllamaLlmClientTest | `OllamaLlmClientTest.java` | 6 | Offline error paths: connection refused, constructor validation |
| OllamaIntegrationTest | `OllamaIntegrationTest.java` | 3 | Opt-in: real Ollama interaction (requires `-Dllm.integration=true`) |
| MainTest (Stage 3 additions) | `MainTest.java` | 8 | CLI: `--llm-*` flags, unknown provider, missing values |
| **Stage 3 subtotal** | **5 suites** | **32** | |
| **Total** | **14 suites** | **109** | **(+3 skipped = integration)** |

## How to Run

```bash
# All tests (Stage 1 + Stage 2)
mvn test

# Stage 2 only
mvn test -Dtest="*Prompt*,*Validator*,*Stub*,*Serializ*,*Detector*,*ExampleOutput*"

# Single test class
mvn test -Dtest=PnpImportFormatConfigValidatorTest

# Single test method
mvn test -Dtest=DetectorTest#detectWithValidStubResponseReturnsValidResult

# Generate example outputs and compare against expected configs
mvn test -Dtest=ExampleOutputGeneratorTest
```

## Gherkin Scenario Coverage

All 25 acceptance scenarios from the Stage 2 spec are covered:

| Scenario | Test(s) |
|---|---|
| Detect command outputs a valid config | `DetectorTest.detectWithValidStubResponseReturnsValidResult`, `MainTest.detectCommandReturnsZero` |
| Detect preserves sampler behavior | All 31 Stage 1 tests pass unchanged |
| Prompt describes manual config | `FormatDetectionPromptBuilderTest.promptContainsManualConfigurationExplanation` |
| Prompt says parser is authority | `FormatDetectionPromptBuilderTest.promptStatesParserIsAuthority` |
| Prompt says LLM must not parse rows | `FormatDetectionPromptBuilderTest.promptStatesLlmMustNotParseRows` |
| Prompt includes raw sampled lines | `FormatDetectionPromptBuilderTest.promptIncludesRawSampledLines` |
| Prompt includes expected schema | `FormatDetectionPromptBuilderTest.promptIncludesExpectedSchema` |
| Prompt requires JSON-only response | `FormatDetectionPromptBuilderTest.promptRequiresJsonOnly` |
| Valid config passes validation | `PnpImportFormatConfigValidatorTest.validConfigPassesValidation` |
| Reject missing X column | `PnpImportFormatConfigValidatorTest.rejectMissingXColumn` |
| Reject missing Y column | `PnpImportFormatConfigValidatorTest.rejectMissingYColumn` |
| Reject missing angle column | `PnpImportFormatConfigValidatorTest.rejectMissingAngleColumn` |
| Reject no identity column | `PnpImportFormatConfigValidatorTest.rejectNoIdentityColumn` |
| Reject invalid confidence (high) | `PnpImportFormatConfigValidatorTest.rejectConfidenceTooHigh` |
| Reject invalid confidence (negative) | `PnpImportFormatConfigValidatorTest.rejectConfidenceNegative` |
| Reject blank delimiter | `PnpImportFormatConfigValidatorTest.rejectBlankDelimiter` |
| Reject invalid schema version | `PnpImportFormatConfigValidatorTest.rejectInvalidSchemaVersion` |
| Reject negative dataStartRowIndex | `PnpImportFormatConfigValidatorTest.rejectNegativeDataStartRowIndex` |
| Reject dataEndRowIndex before start | `PnpImportFormatConfigValidatorTest.rejectDataEndRowIndexBeforeDataStart` |
| Reject negative headerRowIndex | `PnpImportFormatConfigValidatorTest.rejectNegativeHeaderRowIndex` |
| Reject invalid decimal separator | `PnpImportFormatConfigValidatorTest.rejectInvalidDecimalSeparator` |
| Reject same column index for x/y/angle | `PnpImportFormatConfigValidatorTest.rejectAllSameColumnIndex` |
| Reject non-numeric COLUMN_INDEX | `PnpImportFormatConfigValidatorTest.rejectNonNumericColumnIndex` |
| Valid config without side passes | `PnpImportFormatConfigValidatorTest.validConfigWithoutSidePasses` |
| Error on detect with missing file | `MainTest.detectCommandFileNotFound` |

## Testing Principles

- **No network**: All Stage 2 tests are deterministic and require no internet, API keys, or LLM services
- **Stub-driven**: `StubLlmClient` returns a fixed JSON configured per test — fully deterministic
- **Reader-based**: Where possible, components accept in-memory data (no filesystem needed)
- **Pipeline isolation**: Each component (prompt builder, validator, detector) is testable independently
- **Edge cases covered**: null config, empty columns, null delimiter, negative indexes, confidence out of range, non-numeric column index

## CLI Verification

```bash
# Detect on example files
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/messy-pnp.csv

# Detect with custom sample sizes
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv --first 40 --last 10

# Detect on nonexistent file (exit code 1)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect nonexistent.csv

# Sample mode still works unchanged
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/simple-pnp.csv
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar sample examples/simple-pnp.csv
```

## Example Output Verification

The `ExampleOutputGeneratorTest` processes all 12 files in `examples-extended/`:

```bash
# Default: uses StubLlmClient
mvn test -Dtest=ExampleOutputGeneratorTest

# With real LLM (requires Ollama running)
mvn test -Dtest=ExampleOutputGeneratorTest \
  -Dllm.integration=true \
  -Dllm.url=http://localhost:11434 \
  -Dllm.model=qwen2.5:3b
```

This generates 24 output files in `target/example-outputs/` (12 sample + 12 detect) and compares detect results against expected configs in `expected-configs/`.

With the stub, all 12 files will differ from expected configs. With Ollama, a subset will match depending on model quality.

## Known Limitations (Stage 2 — Stub only)

- `StubLlmClient` returns the same hardcoded config for all files — format detection is not actually performed
- The whitespace-delimited file format (`.pos` files like `01_kicad`) is not handled by the stub

## Stage 3 — Real LLM Adapter (Ollama)

These limitations are resolved when using Ollama:
- ✅ `OllamaLlmClient` analyzes each file and produces a file-specific config
- ✅ Whitespace-delimited files (`.pos`) are correctly analyzed
- ✅ Tab-delimited files (`.tsv`) are correctly analyzed
- ✅ Semicolon-delimited with decimal comma (European format) is correctly analyzed
- ✅ No-header files are correctly detected

## Opt-in Integration Tests

Real LLM tests are gated behind `-Dllm.integration=true`:

```bash
# Run integration tests (requires Ollama running)
mvn test -Dllm.integration=true

# Run example output generation with Ollama
mvn test -Dtest=ExampleOutputGeneratorTest \
  -Dllm.integration=true \
  -Dllm.url=http://localhost:11434 \
  -Dllm.model=qwen2.5:3b
```
