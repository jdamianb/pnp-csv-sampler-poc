# Feature Request: LLM-assisted PnP Import Format Detection

## Context

Stage 1 is complete: the project already contains a CSV sampler.

The sampler reads a PnP CSV/TSV-like file and produces a numbered raw sample JSON.

Today, users manually configure how a PnP file should be imported. They tell the system:

- which lines to ignore
- which row contains the header
- where the data starts
- which column is reference/designator
- which column is part number
- which column is JEDEC/package/footprint
- which column is X
- which column is Y
- which column is angle/rotation
- which column is side/layer, if available
- which decimal separator is used
- which units are used

The existing CSV parser already exists and remains the authority for parsing.

## Goal

Reduce manual PnP import setup by asking an LLM to propose the parser configuration automatically.

The LLM must not parse the full file and must not produce normalized component rows.

The LLM only proposes the configuration that the existing parser needs.

## Input

A `CsvSample` JSON produced by the existing sampler.

## Output

A `PnpImportFormatConfig` JSON.

The output should represent the same manual settings that a user currently provides before the existing parser can parse the file.

## Required workflow

```text
PnP file
  ↓
Existing CsvSampler
  ↓
CsvSample JSON
  ↓
LLM Format Detector
  ↓
PnpImportFormatConfig JSON
  ↓
Config validation
  ↓
Output proposed import configuration
```

## Important boundary

The LLM is a format-configuration assistant.

The LLM is not:

- a CSV parser
- a replacement for the existing parser
- a component placement normalizer
- a source of truth

The existing parser remains the deterministic authority.

## Scope

Implement:

- `PnpImportFormatConfig`
- `ColumnMapping`
- `ColumnSource`
- `FormatDetectionPromptBuilder`
- `PnpImportFormatConfigValidator`
- `LlmClient` abstraction
- `StubLlmClient` for deterministic tests
- CLI command: `detect <file>`
- tests for prompt building, config parsing, and config validation

The first implementation must use `StubLlmClient`.

Real LLM calls are not required for the first Stage 2 implementation.

## Optional scope after stub implementation is approved

After the stubbed detector passes tests and is approved by the human, the team may add one local or cloud LLM adapter.

Preferred local options:

- Ollama-compatible client
- OpenAI-compatible local endpoint, such as LM Studio, llama.cpp server, or vLLM

Possible cloud fallback:

- DeepSeek/OpenAI-compatible client

## Out of scope

Do not implement:

- Spring Boot
- database
- web UI
- RAG
- fine-tuning
- replacement CSV parser
- component-row normalization by the LLM
- direct parser integration unless explicitly approved
- automatic correction UI
- production persistence of detected formats

## Expected config shape

```json
{
  "schemaVersion": 1,
  "confidence": 0.91,
  "delimiter": ";",
  "quoteChar": "\"",
  "encoding": "UTF-8",
  "linesToIgnore": [0, 1, 2, 3, 4, 10, 11],
  "headerRowIndex": 5,
  "dataStartRowIndex": 6,
  "dataEndRowIndex": 9,
  "decimalSeparator": ",",
  "columns": {
    "reference": {
      "source": "HEADER_NAME",
      "value": "RefDes"
    },
    "partNumber": {
      "source": "HEADER_NAME",
      "value": "PartNo"
    },
    "jedec": {
      "source": "HEADER_NAME",
      "value": "Package"
    },
    "x": {
      "source": "HEADER_NAME",
      "value": "X-Pos"
    },
    "y": {
      "source": "HEADER_NAME",
      "value": "Y-Pos"
    },
    "angle": {
      "source": "HEADER_NAME",
      "value": "Angle"
    },
    "side": {
      "source": "HEADER_NAME",
      "value": "MountSide"
    }
  },
  "units": {
    "x": "mm",
    "y": "mm",
    "angle": "deg"
  },
  "valueMappings": {
    "side": {
      "T": "Top",
      "B": "Bottom"
    }
  },
  "warnings": []
}
```

## Column mapping rules

A column mapping must support at least these source types:

```text
HEADER_NAME
COLUMN_INDEX
```

Use `HEADER_NAME` when a header row exists.

Use `COLUMN_INDEX` when the file has no reliable header.

Example with header:

```json
{
  "source": "HEADER_NAME",
  "value": "X-Pos"
}
```

Example without header:

```json
{
  "source": "COLUMN_INDEX",
  "value": "4"
}
```

Column indexes are zero-based unless the approved spec says otherwise.

## Required canonical fields

The first version should support these canonical fields:

- `reference`
- `partNumber`
- `jedec`
- `x`
- `y`
- `angle`
- `side`

Minimum required fields for a useful config:

- `x`
- `y`
- `angle`
- at least one identity field:
  - `reference`
  - `partNumber`
  - `jedec`

The field `side` may be optional because some files may contain only one side or may not expose side information.

## Validation rules

The config validator must reject configs when:

- `schemaVersion` is not `1`
- `confidence` is less than `0` or greater than `1`
- `delimiter` is blank
- `dataStartRowIndex` is negative
- `headerRowIndex` is negative when present
- `dataEndRowIndex` is lower than `dataStartRowIndex` when present
- `decimalSeparator` is not `"."` or `","`
- `x` column is missing
- `y` column is missing
- `angle` column is missing
- no identity column is present
- `x`, `y`, and `angle` point to the same column
- `COLUMN_INDEX` mapping values are not valid integers

## CLI behavior

The existing sampler behavior must continue to work.

Add a new command:

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv
```

The command should:

1. Run the existing sampler.
2. Build a prompt from the sample.
3. Use `StubLlmClient` in the first implementation.
4. Parse the returned JSON as `PnpImportFormatConfig`.
5. Validate the config.
6. Print pretty JSON for the proposed config.

## Prompt requirements

The prompt builder must clearly state:

- the LLM replaces the manual import configuration step
- the existing parser remains the authority
- the LLM must not parse component rows
- the LLM must not rewrite the CSV
- the LLM must not invent component data
- the LLM must return only JSON
- the output must match the expected schema

The prompt must include the raw numbered sample produced by the sampler.

## Acceptance criteria

- Existing sampler behavior still works.
- Existing tests still pass.
- `mvn test` passes.
- `detect <file>` outputs valid JSON.
- Detector output can be parsed into `PnpImportFormatConfig`.
- Validator accepts a valid config.
- Validator rejects configs missing X, Y, or angle.
- Validator rejects configs without any identity column.
- Validator rejects invalid row indexes.
- Validator rejects invalid confidence values.
- Validator rejects blank delimiters.
- Unit tests do not require network access or a real LLM.
- Stub LLM tests are deterministic.
- Prompt builder tests verify that the prompt contains the manual-configuration objective.
- Prompt builder tests verify that the prompt includes the sampled raw lines.

## Human gate

The spec writer must produce EARS requirements and Gherkin scenarios first.

Implementation must not begin until the human approves the Stage 2 spec.
