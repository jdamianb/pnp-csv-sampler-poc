# Feature: LLM-assisted PnP Import Format Detection

## Summary

A pipeline that receives a `CsvSample` (from the Stage 1 sampler), builds a prompt for an LLM, parses the LLM's JSON response into a `PnpImportFormatConfig`, validates it, and outputs the proposed import configuration.

The first implementation uses a deterministic `StubLlmClient`. Real LLM calls are deferred to Stage 3.

## Status

✅ Implemented, tested, reviewed, and approved (Stage 2 — Stub).

## Architecture

```
CLI: detect <file>
  │
  ├── Sampler (Stage 1) → SampleResult
  │
  ├── FormatDetectionPromptBuilder → prompt string
  │
  ├── LlmClient (interface)
  │     └── StubLlmClient (Stage 2)
  │         └── (Real LLM adapter in Stage 3)
  │
  ├── Detector orchestrator
  │     ├── Builds prompt
  │     ├── Sends to LLM
  │     ├── Parses JSON → PnpImportFormatConfig
  │     └── Validates config
  │
  └── Output: config JSON (or validation errors)
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
| `Main.java` (modified) | 202 | Added `detect <file>` CLI command |

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

# New detect mode
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv

# Detect with custom sample sizes
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv --first 40 --last 10
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

## Verification Tool

The `ExampleOutputGeneratorTest` processes all files in `examples-extended/` and:
1. Generates sample + detect JSON outputs in `target/example-outputs/`
2. Compares detect output against expected configs in `expected-configs/`
3. Reports matches/mismatches without failing the test (mismatches expected in Stage 2)
