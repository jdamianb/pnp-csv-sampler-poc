# Specification: LLM-assisted PnP Import Format Detection

## Purpose

Reduce manual PnP import configuration by allowing an LLM to propose the parser configuration needed by the existing CSV parser.

## Scope

This feature receives a `CsvSample` produced by the existing sampler and produces a validated `PnpImportFormatConfig`.

The first implementation must use a deterministic `StubLlmClient`.

## Out of scope

- Spring Boot
- database
- web UI
- RAG
- fine-tuning
- replacing the existing parser
- asking the LLM to parse component rows
- production persistence
- real LLM network calls, unless separately approved
- parser dry-run integration, unless separately approved

## Assumptions

- The CSV sampler already exists.
- The existing CSV parser already exists outside this feature.
- The existing parser remains the authority for parsing.
- Users currently configure import formats manually.
- The LLM should produce only the manual configuration, not normalized placement rows.

## EARS requirements

### Ubiquitous requirements

- The system shall preserve the existing CSV sampler behavior.
- The system shall represent detected import settings as `PnpImportFormatConfig`.
- The system shall validate every proposed `PnpImportFormatConfig` before printing it as accepted output.
- The system shall not require network access for unit tests.
- The system shall not call a real LLM during deterministic tests.
- The system shall not use Spring Boot.

### Event-driven requirements

- When the user runs `detect <file>`, the system shall sample the file using the existing CSV sampler.
- When the system has a `CsvSample`, the system shall build a format-detection prompt from the sample.
- When the LLM client returns JSON, the system shall parse it as `PnpImportFormatConfig`.
- When the parsed config is valid, the system shall print the config as pretty JSON.
- When the parsed config is invalid, the system shall return a clear validation error.

### State-driven requirements

- While building the prompt, the system shall state that the LLM replaces the manual import configuration step.
- While building the prompt, the system shall state that the existing parser remains the authority.
- While building the prompt, the system shall state that the LLM must not parse component rows.
- While using the stub LLM, the system shall return deterministic JSON suitable for tests.

### Unwanted behavior requirements

- If the config is missing the X column, then the validator shall reject it.
- If the config is missing the Y column, then the validator shall reject it.
- If the config is missing the angle column, then the validator shall reject it.
- If the config has no identity column, then the validator shall reject it.
- If the delimiter is blank, then the validator shall reject it.
- If the confidence is outside the range 0 to 1, then the validator shall reject it.
- If row indexes are invalid, then the validator shall reject the config.
- If a `COLUMN_INDEX` mapping is not numeric, then the validator shall reject the config.

## Expected config model

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
    "reference": {"source": "HEADER_NAME", "value": "RefDes"},
    "partNumber": {"source": "HEADER_NAME", "value": "PartNo"},
    "jedec": {"source": "HEADER_NAME", "value": "Package"},
    "x": {"source": "HEADER_NAME", "value": "X-Pos"},
    "y": {"source": "HEADER_NAME", "value": "Y-Pos"},
    "angle": {"source": "HEADER_NAME", "value": "Angle"},
    "side": {"source": "HEADER_NAME", "value": "MountSide"}
  },
  "units": {"x": "mm", "y": "mm", "angle": "deg"},
  "valueMappings": {"side": {"T": "Top", "B": "Bottom"}},
  "warnings": []
}
```

## Gherkin acceptance scenarios

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

  Scenario: Prompt describes manual import configuration
    Given a CsvSample
    When the system builds the LLM prompt
    Then the prompt shall explain that the LLM replaces the manual import configuration step
    And the prompt shall explain that the LLM must not parse component rows
    And the prompt shall include the sampled raw lines

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

  Scenario: Reject invalid confidence
    Given a proposed config with confidence greater than 1
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: Reject blank delimiter
    Given a proposed config with a blank delimiter
    When the validator validates the config
    Then the validator shall reject the config

  Scenario: Preserve sampler behavior
    Given the existing sample command
    When Stage 2 is implemented
    Then the sample command shall continue to work
    And all existing sampler tests shall still pass
```

## Non-functional requirements

- The implementation should remain small and suitable for a PoC.
- The implementation should be deterministic in tests.
- The implementation should keep real LLM integration behind an interface.
- The implementation should avoid unnecessary frameworks.
- The implementation should be easy to replace later with a local LLM client.

## Testability notes

Unit tests should cover:

- prompt builder output
- parsing config JSON into DTOs
- validator accepting valid configs
- validator rejecting invalid configs
- CLI detect command using `StubLlmClient`
- preservation of existing sampler tests

No test should require:

- internet access
- API keys
- local LLM process
- external parser process

## Human review checklist

- [ ] Scope is limited to configuration detection.
- [ ] The existing parser remains the authority.
- [ ] The LLM is not asked to parse component rows.
- [ ] Stubbed tests are enough for the first implementation.
- [ ] Real LLM integration is deferred or explicitly approved.
- [ ] Gherkin scenarios are testable.
- [ ] Implementation may begin.
