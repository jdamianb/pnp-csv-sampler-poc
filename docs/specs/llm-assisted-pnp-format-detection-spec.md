# Specification: LLM-assisted PnP Import Format Detection

## 1. Purpose

Reduce the manual setup required to import Pick-and-Place CSV/TSV files by having an LLM propose the parser configuration automatically — without parsing component rows or replacing the existing deterministic parser.

The LLM acts as a configuration assistant. It receives the raw numbered sample from Stage 1's sampler and proposes a `PnpImportFormatConfig` that a human would otherwise enter by hand.

## 2. Scope

This feature receives a `CsvSample` JSON (produced by the existing sampler), builds a prompt for an LLM, parses the LLM's JSON response into a `PnpImportFormatConfig`, validates it, and outputs the proposed import configuration.

The first implementation uses a deterministic `StubLlmClient`. No real LLM network calls are required for this stage.

The feature includes:

- `PnpImportFormatConfig` — the config data model
- `ColumnMapping` — column mapping model with source type and value
- `ColumnSource` — enum for `HEADER_NAME` and `COLUMN_INDEX`
- `FormatDetectionPromptBuilder` — builds the prompt from a `CsvSample`
- `LlmClient` — interface/abstraction for LLM calls
- `StubLlmClient` — deterministic stub for testing
- `PnpImportFormatConfigValidator` — validates proposed configs
- `detect <file>` CLI command — wires sampler → prompt → LLM → validate → output
- Unit tests for all of the above

## 3. Out of Scope

The following are **explicitly excluded** from Stage 2:

- Real LLM network calls (Ollama, OpenAI, DeepSeek, etc.) — deferred to Stage 3
- Spring Boot
- Database storage or persistence
- Web UI or GUI
- RAG or vector storage
- Fine-tuning
- Replacing the existing CSV parser
- Asking the LLM to parse component placement rows
- Component-row normalization or transformation by the LLM
- Direct integration with the existing parser — deferred to Stage 4
- Automatic repair or correction loop — deferred to Stage 5
- Human review/edit UI — deferred to Stage 6
- Evaluation dataset or metrics — deferred to Stage 7

## 4. Assumptions

- The Stage 1 CSV sampler already exists and works.
- The existing deterministic CSV parser exists outside this feature and remains the authority for parsing.
- The existing parser's `sample` command continues to work unchanged.
- Users currently configure PnP imports manually (delimiter, columns, units, etc.).
- The LLM should produce only the manual configuration structure — not normalized placement data.
- The `CsvSample` JSON produced by the sampler is available and contains `totalLines`, `firstLines`, and `lastLines`.
- The `detect` command will sample the file first using the existing sampler.
- All Stage 2 tests must be deterministic and require no network access.
- Column indexes are zero-based.

## 5. EARS Requirements

### 5.1 Ubiquitous

```
R01  The system shall preserve the existing CSV sampler behavior and all existing tests.
R02  The system shall represent detected import settings as PnpImportFormatConfig.
R03  The system shall validate every proposed PnpImportFormatConfig before printing it as accepted output.
R04  The system shall not require network access for unit tests.
R05  The system shall not call a real LLM during deterministic tests.
R06  The system shall not use Spring Boot.
R07  The system shall support at least two column source types: HEADER_NAME and COLUMN_INDEX.
R08  The system shall support at least these canonical fields: reference, partNumber, jedec, x, y, angle, side.
```

### 5.2 Event-driven (When)

```
R09  When the user runs "detect <file>", the system shall sample the file using the existing CSV sampler.
R10  When the system has a CsvSample, the system shall build a format-detection prompt from the sample.
R11  When the LLM client returns JSON, the system shall parse it as PnpImportFormatConfig.
R12  When the parsed config is valid, the system shall print the config as pretty JSON to stdout.
R13  When the parsed config is invalid, the system shall print validation errors to stderr and exit with code 1.
```

### 5.3 State-driven (While)

```
R14  While building the prompt, the system shall state that the LLM replaces the manual import configuration step.
R15  While building the prompt, the system shall state that the existing parser remains the authority.
R16  While building the prompt, the system shall state that the LLM must not parse component rows.
R17  While building the prompt, the system shall state that the LLM must only return valid JSON matching the expected schema.
R18  While building the prompt, the system shall include the raw numbered sampled lines from the CsvSample.
R19  While using the stub LLM client, the system shall return deterministic JSON suitable for tests.
```

### 5.4 Unwanted behavior (If)

```
R20  If the config is missing an X column mapping, then the validator shall reject it.
R21  If the config is missing a Y column mapping, then the validator shall reject it.
R22  If the config is missing an angle column mapping, then the validator shall reject it.
R23  If the config has no identity column (reference, partNumber, or jedec), then the validator shall reject it.
R24  If the delimiter is blank or empty, then the validator shall reject it.
R25  If the confidence is less than 0 or greater than 1, then the validator shall reject it.
R26  If schemaVersion is not 1, then the validator shall reject it.
R27  If dataStartRowIndex is negative, then the validator shall reject it.
R28  If dataEndRowIndex is present and lower than dataStartRowIndex, then the validator shall reject it.
R29  If headerRowIndex is present and negative, then the validator shall reject it.
R30  If decimalSeparator is neither "." nor ",", then the validator shall reject it.
R31  If x, y, and angle all point to the same column, then the validator shall reject it.
R32  If a COLUMN_INDEX mapping value is not a valid integer, then the validator shall reject it.
```

### 5.5 Where (feature)

```
R33  Where the StubLlmClient is used, the system shall define the stub's response declaratively in the test.
```

## 6. Gherkin Acceptance Scenarios

```gherkin
Feature: LLM-assisted PnP import format detection

  Background:
    Given the CSV sampler already exists
    And the existing CSV parser remains the authority for parsing

  Scenario: Detect command outputs a valid config
    Given a PnP CSV file
    And the detector uses a deterministic StubLlmClient
    When the user runs the detect command
    Then the system shall output valid JSON
    And the JSON shall parse as PnpImportFormatConfig
    And the config shall pass validation

  Scenario: Detect command preserves sampler behavior
    Given the existing sample command
    When Stage 2 is implemented
    Then the sample command shall continue to work
    And all existing sampler tests shall still pass

  Scenario: Prompt describes manual import configuration
    Given a CsvSample
    When the system builds the LLM prompt
    Then the prompt shall explain that the LLM replaces the manual import configuration step
    And the prompt shall explain that the LLM must not parse component rows
    And the prompt shall include the sampled raw lines
    And the prompt shall explain that the LLM must return valid JSON matching the schema

  Scenario: Prompt includes raw sampled lines
    Given a CsvSample with 8 total lines
    When the system builds the LLM prompt
    Then the prompt shall contain the raw text of line index 0
    And the prompt shall contain the raw text of line index 7

  Scenario: Valid config passes validation
    Given a complete PnpImportFormatConfig with all required fields
    When the validator validates the config
    Then the validator shall accept the config

  Scenario: Reject missing X column
    Given a proposed config without an X column mapping
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: Reject missing Y column
    Given a proposed config without a Y column mapping
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: Reject missing angle column
    Given a proposed config without an angle column mapping
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: Reject config without identity column
    Given a proposed config without reference, partNumber, or jedec mappings
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: Reject invalid confidence (too high)
    Given a proposed config with confidence 1.5
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: Reject invalid confidence (negative)
    Given a proposed config with confidence -0.5
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: Reject blank delimiter
    Given a proposed config with a blank delimiter
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: Reject invalid schema version
    Given a proposed config with schemaVersion 2
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: Reject negative dataStartRowIndex
    Given a proposed config with dataStartRowIndex -1
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: Reject dataEndRowIndex before dataStartRowIndex
    Given a proposed config with dataStartRowIndex 10 and dataEndRowIndex 5
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: Reject negative headerRowIndex
    Given a proposed config with headerRowIndex -1
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: Reject invalid decimal separator
    Given a proposed config with decimalSeparator ";"
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: Reject all columns pointing to the same index
    Given a proposed config where x, y, and angle all map to COLUMN_INDEX 2
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: Reject non-numeric COLUMN_INDEX
    Given a proposed config with a COLUMN_INDEX value of "abc"
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: StubLlmClient returns deterministic JSON
    Given a StubLlmClient configured with a known JSON response
    When the client is called
    Then the client shall return that exact JSON string

  Scenario: Side column is optional
    Given a valid PnpImportFormatConfig without a side column mapping
    When the validator validates the config
    Then the validator shall accept the config

  Scenario: Empty file detection
    Given an empty CSV file
    When the user runs the detect command
    Then the system shall output an error or a config with warnings
    And the system shall not crash

  Scenario: Error on detect with missing file
    Given a non-existent file path
    When the user runs the detect command
    Then the system shall exit with code 1
    And an error message shall be written to stderr
```

## 7. Expected Config Schema

### 7.1 PnpImportFormatConfig

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
    "reference": { "source": "HEADER_NAME", "value": "RefDes" },
    "partNumber": { "source": "HEADER_NAME", "value": "PartNo" },
    "jedec": { "source": "HEADER_NAME", "value": "Package" },
    "x": { "source": "HEADER_NAME", "value": "X-Pos" },
    "y": { "source": "HEADER_NAME", "value": "Y-Pos" },
    "angle": { "source": "HEADER_NAME", "value": "Angle" },
    "side": { "source": "HEADER_NAME", "value": "MountSide" }
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

### 7.2 ColumnMapping

| Field | Type | Description |
|---|---|---|
| `source` | `ColumnSource` enum | `HEADER_NAME` or `COLUMN_INDEX` |
| `value` | `String` | Header name or column index as string |

### 7.3 ColumnSource

| Enum Value | Description |
|---|---|
| `HEADER_NAME` | Column is identified by its header row text |
| `COLUMN_INDEX` | Column is identified by its zero-based index |

## 8. Validation Rules

The `PnpImportFormatConfigValidator` must reject configs when:

| Rule | Condition |
|---|---|
| Schema version | `schemaVersion != 1` |
| Confidence | `confidence < 0 OR confidence > 1` |
| Delimiter | `delimiter == null OR delimiter.isBlank()` |
| dataStartRowIndex | `< 0` |
| dataEndRowIndex | `present AND < dataStartRowIndex` |
| headerRowIndex | `present AND < 0` |
| decimalSeparator | `not "." AND not ","` |
| X column | missing |
| Y column | missing |
| Angle column | missing |
| Identity column | none of reference, partNumber, jedec present |
| All same column | x, y, and angle all point to the same `COLUMN_INDEX` |
| Non-numeric COLUMN_INDEX | any COLUMN_INDEX value is not a parseable integer |

## 9. Non-functional Requirements

| ID | Requirement |
|---|---|
| NF01 | Language: Java 21 |
| NF02 | Build tool: Maven |
| NF03 | Dependencies: Jackson 2.x, JUnit 5.x (no new dependencies beyond Stage 1 unless justified) |
| NF04 | Spring Boot: not allowed |
| NF05 | Deterministic tests: all unit tests must pass without network access, API keys, or external services |
| NF06 | CLI: `detect <file>` command, sampler behavior unchanged |
| NF07 | Extensibility: `LlmClient` must be an interface so real LLM adapters can be added later |
| NF08 | Test isolation: `StubLlmClient` must be configurable per test |

## 10. Testability Notes

- `FormatDetectionPromptBuilder` accepts a `CsvSample` → testable via `StringReader`/sample JSON
- `PnpImportFormatConfigValidator` accepts a config POJO → testable with plain objects
- `StubLlmClient` returns a fixed JSON string set per test → fully deterministic
- The `detect` command can be tested by replacing the `LlmClient` with a stub
- All existing sampler tests (`SamplerTest`, `MainTest`) must continue to pass
- JSON serialization/deserialization of `PnpImportFormatConfig` should be tested with Jackson

## 11. Human Review Checklist

- [ ] Purpose clearly states LLM is a configuration assistant, not a parser
- [ ] Scope limited to config detection with stub LLM
- [ ] Out of scope excludes real LLM calls, Spring Boot, parser replacement, database, UI
- [ ] EARS requirements cover all validation rules (12 rejection rules)
- [ ] Gherkin scenarios cover all validation rejections and prompt behavior
- [ ] Config schema includes all required fields and column source types
- [ ] Validation rules match the feature request
- [ ] The existing parser remains the authority
- [ ] Stub LLM tests are sufficient for Stage 2
- [ ] Real LLM integration is explicitly deferred
- [ ] Implementation may begin after approval
