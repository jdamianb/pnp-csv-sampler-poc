# Feature: LLM-assisted PnP Import Format Detection

## Summary

A pipeline that receives a `CsvSample` (from the Stage 1 sampler), builds a prompt for an LLM, parses the LLM's JSON response into a `PnpImportFormatConfig`, validates it, and outputs the proposed import configuration.

Stage 2 implemented a deterministic `StubLlmClient`. Stage 3 adds a real LLM adapter for Ollama while preserving the stub as the default.

## Status

✅ Stage 2 (Stub): Implemented, tested, reviewed, and approved.
✅ Stage 3 (Ollama): Implemented, tested, reviewed, and approved.

## Architecture

```
CLI: detect <file> [--llm-provider ...]
  │
  ├── Sampler (Stage 1) → SampleResult
  │
  ├── FormatDetectionPromptBuilder → prompt string
  │
  ├── LlmClientFactory
  │     ├── provider=null/blank → StubLlmClient (default)
  │     └── provider="ollama"   → OllamaLlmClient
  │
  ├── Detector orchestrator
  │     ├── Builds prompt
  │     ├── Sends to LLM
  │     ├── Parses JSON → PnpImportFormatConfig
  │     └── Validates config
  │
  └── Output: config JSON (or validation/LLM errors)
```

## Source Files

| File | Lines | Description |
|---|---|---|
| `ColumnSource.java` | 11 | Enum: `HEADER_NAME`, `COLUMN_INDEX` |
| `ColumnMapping.java` | 14 | Record: source type + column identifier |
| `PnpImportFormatConfig.java` | 44 | Config model: 14 fields |
| `LlmClient.java` | 18 | Interface: `sendPrompt(String)` |
| `StubLlmClient.java` | 33 | Deterministic stub for testing |
| `FormatDetectionPromptBuilder.java` | 100 | Builds prompt from `SampleResult` |
| `PnpImportFormatConfigValidator.java` | 121 | 12 validation rules |
| `Detector.java` | 82 | Orchestrator: prompt → LLM → parse → validate |
| `Main.java` (modified) | 304 | CLI: `detect` with `--llm-*` flags + env var support |
| `LlmOptions.java` | 31 | Provider config: provider, baseUrl, model, temperature |
| `OllamaLlmClient.java` | 136 | Real LLM client using Java `HttpClient` |
| `LlmException.java` | 18 | Runtime exception for LLM errors |
| `LlmClientFactory.java` | 48 | Factory: stub by default, Ollama when configured |

## PnpImportFormatConfig Schema

```json
{
  "schemaVersion": 1,
  "confidence": 0.91,
  "delimiter": ";",
  "quoteChar": "\"",
  "encoding": "UTF-8",
  "linesToIgnore": [0, 1, 2, 3, 4],
  "headerRowIndex": 5,
  "dataStartRowIndex": 6,
  "dataEndRowIndex": 9,
  "decimalSeparator": ",",
  "columns": {
    "reference": { "source": "HEADER_NAME", "value": "RefDes" },
    "partNumber": { "source": "HEADER_NAME", "value": "PartNo" },
    "jedec": { "source": "HEADER_NAME", "value": "Package" },
    "x": { "source": "HEADER_NAME", "value": "X-Pos" },
    "y": { "source": "HEADER_NAME", "value": "Y-Pos" },
    "angle": { "source": "HEADER_NAME", "value": "Angle" },
    "side": { "source": "HEADER_NAME", "value": "MountSide" }
  },
  "units": { "x": "mm", "y": "mm", "angle": "deg" },
  "valueMappings": { "side": { "T": "Top", "B": "Bottom" } },
  "warnings": []
}
```

## Column Source Types

| Source | Description | Example |
|---|---|---|
| `HEADER_NAME` | Column is identified by its header row text | `"value": "RefDes"` |
| `COLUMN_INDEX` | Column is identified by its zero-based index | `"value": "4"` |

## CLI Usage

```bash
# Existing sample mode (unchanged)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/simple-pnp.csv
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar sample examples/simple-pnp.csv

# Detect with stub (default — no LLM required)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv

# Detect with Ollama (real LLM analysis)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv \
  --llm-provider ollama \
  --llm-url http://localhost:11434 \
  --llm-model qwen2.5:3b

# Detect with Ollama using environment variables
PNP_LLM_PROVIDER=ollama PNP_LLM_BASE_URL=http://localhost:11434 \
PNP_LLM_MODEL=qwen2.5:3b \
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv

# Detect with custom sample sizes and LLM flags
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv \
  --first 40 --last 10 \
  --llm-provider ollama \
  --llm-url http://localhost:11434 \
  --llm-model qwen2.5:3b \
  --llm-temperature 0
```

## Example Output

```bash
$ java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv
```
```json
{
  "schemaVersion" : 1,
  "confidence" : 0.85,
  "delimiter" : ",",
  "quoteChar" : "\"",
  ...
}
```

## Validation Rules (12 rules)

| Rule | Condition |
|---|---|
| Schema version | `schemaVersion != 1` → reject |
| Confidence range | `< 0 or > 1` → reject |
| Delimiter blank | `null or blank` → reject |
| Decimal separator | not `.` or `,` → reject |
| dataStartRowIndex | `< 0` → reject |
| dataEndRowIndex | present and < dataStartRowIndex → reject |
| headerRowIndex | present and < 0 → reject |
| Missing X | not in columns → reject |
| Missing Y | not in columns → reject |
| Missing angle | not in columns → reject |
| No identity column | none of reference, partNumber, jedec → reject |
| x/y/angle all same index | all COLUMN_INDEX with same value → reject |
| Non-numeric COLUMN_INDEX | value not parseable as integer → reject |

## Design Decisions

- **Stub first**: The `LlmClient` interface allows swapping stubs for real LLMs without changing the pipeline
- **Interface over abstract class**: `LlmClient` is a single-method interface — minimal contract, easy to implement
- **Detector orchestrator**: Single entry point for the 5-step pipeline, testable via constructor injection
- **R10 dedup preserved**: The sampler's dedup rule (no overlapping lines) is inherited from Stage 1
- **Java HttpClient**: Uses Java's built-in `java.net.http.HttpClient` — zero new dependencies for Stage 3
- **Factory pattern**: `LlmClientFactory` encapsulates provider selection logic, easy to extend with new providers
- **CLI args > env vars**: CLI flags override environment variables for explicit per-invocation control
- **Temperature 0**: Default temperature ensures deterministic LLM output for configuration detection
- **Stub as default**: If no provider is configured, `detect` uses `StubLlmClient` — works offline

## Verification Tool

The `ExampleOutputGeneratorTest` processes all files in `examples-extended/` and:
1. Generates sample + detect JSON outputs in `target/example-outputs/`
2. Compares detect output against expected configs in `expected-configs/`
3. Reports matches/mismatches without failing the test

To run with the stub (default):
```bash
mvn test -Dtest=ExampleOutputGeneratorTest
```

To run with Ollama for real LLM analysis:
```bash
mvn test -Dtest=ExampleOutputGeneratorTest \
  -Dllm.integration=true \
  -Dllm.url=http://localhost:11434 \
  -Dllm.model=qwen2.5:3b
```
