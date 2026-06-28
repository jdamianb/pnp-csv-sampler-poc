# Specification: Stage 4 Simple Open PnP Parser Dry-run

## Purpose

Implement a simple, original parser that uses `PnpImportFormatConfig` to dry-run parse PnP CSV/TSV-like files.

The parser exists only for the public PoC because the real company parser is proprietary and unavailable.

## Scope

The parser shall:

- read a file
- apply a proposed config
- produce normalized placement preview rows
- produce a dry-run report
- expose row-level errors
- validate whether X, Y, and angle can be parsed as numbers

## Out of scope

- proprietary parser integration
- production-grade parser behavior
- UI
- database
- RAG
- fine-tuning
- repair loop
- section-aware parsing
- IPC/CAD semantics
- company-specific machine behavior
- parsing rows with an LLM

## Assumptions

- `PnpImportFormatConfig` already exists.
- `PnpImportFormatConfigValidator` already exists.
- The input examples are synthetic and safe to publish.
- Stage 4 parser is original code written for this PoC.
- The parser does not need to support every possible machine format.

## EARS requirements

### Ubiquitous requirements

- The system shall provide a simple original parser for the public PoC.
- The system shall not use proprietary company parser code or data.
- The system shall use `PnpImportFormatConfig` as the source of parsing instructions.
- The system shall output a JSON dry-run report.
- The system shall preserve existing sampler, detector, and LLM adapter behavior.
- The system shall keep all default tests deterministic and offline.

### Event-driven requirements

- When the user runs `parse <file> <config-json>`, the system shall read the file and config.
- When the config is invalid, the system shall reject parsing and report validation errors.
- When the config is valid, the system shall parse candidate data rows according to the config.
- When a row parses successfully, the system shall add a normalized `PnpPlacement` to the report sample.
- When a row fails parsing, the system shall count it as rejected and add a row-level error.
- When parsing finishes, the system shall print `PnpParseDryRunReport` as pretty JSON.

### State-driven requirements

- While resolving columns by header name, the system shall use `headerRowIndex`.
- While resolving columns by column index, the system shall treat indexes as zero-based.
- While parsing numeric values, the system shall support decimal separator `.` and `,`.
- While parsing coordinate values, the system shall strip simple unit suffixes such as `mm`.
- While parsing side values, the system shall apply `valueMappings.side` when present.

### Unwanted behavior requirements

- If the delimiter is unsupported, then the system shall reject parsing with a clear error.
- If a required column cannot be resolved, then the system shall reject parsing with a clear error.
- If X, Y, or angle cannot be parsed for a row, then the system shall reject that row and continue when possible.
- If no rows can be parsed successfully, then the dry-run report shall have `success=false`.
- If the file has no header and a mapping uses `HEADER_NAME`, then the system shall reject parsing with a clear error.
- If a `COLUMN_INDEX` mapping is out of range, then the system shall reject parsing with a clear error.

## Suggested DTOs

```java
public record PnpPlacement(
        String reference,
        String partNumber,
        String jedec,
        double x,
        double y,
        double angle,
        String side
) {
}
```

```java
public record PnpParseDryRunReport(
        boolean success,
        int totalCandidateRows,
        int parsedRows,
        int rejectedRows,
        List<String> errors,
        List<String> warnings,
        List<String> availableColumns,
        List<PnpPlacement> samplePlacements
) {
}
```

## Gherkin acceptance scenarios

```gherkin
Feature: Simple PnP parser dry-run

  Background:
    Given the project has a PnpImportFormatConfig model
    And the parser is an original PoC parser
    And no proprietary parser code is used

  Scenario: Parse comma-delimited file with header mappings
    Given a comma-delimited PnP file with a header row
    And a config mapping X, Y, angle, and identity columns by header name
    When the user runs the parse command
    Then the parser shall output a successful dry-run report
    And the report shall include normalized sample placements

  Scenario: Parse semicolon file with decimal comma
    Given a semicolon-delimited PnP file using comma decimal separators
    And a config with decimalSeparator ","
    When the parser runs
    Then X, Y, and angle values shall be parsed numerically

  Scenario: Parse tab-delimited file
    Given a tab-delimited PnP file
    And a config with delimiter "\t"
    When the parser runs
    Then the parser shall resolve columns and parse valid rows

  Scenario: Parse no-header file with column indexes
    Given a PnP file without a header row
    And a config using COLUMN_INDEX mappings
    When the parser runs
    Then the parser shall parse valid rows using zero-based indexes

  Scenario: Reject missing required mapping
    Given a config missing the X column mapping
    When the user runs the parse command
    Then the parser shall reject the config before parsing

  Scenario: Report invalid numeric row
    Given a file row where X is not numeric
    And a config mapping X to that column
    When the parser runs
    Then the row shall be rejected
    And the report shall contain a row-level error

  Scenario: Reject unsupported delimiter
    Given a config with an unsupported delimiter
    When the parser runs
    Then the report shall indicate the delimiter is unsupported

  Scenario: Preserve previous stages
    Given Stage 4 is implemented
    When the test suite runs
    Then sampler tests shall still pass
    And detector tests shall still pass
    And LLM integration tests shall remain opt-in
```

## Non-functional requirements

- The parser should be small and readable.
- The parser should be deterministic.
- The parser should be easy to replace later.
- The parser should avoid unnecessary dependencies unless the spec explicitly approves one.
- The parser should fail clearly rather than silently guessing.

## Testability notes

Tests should cover:

- comma delimiter
- semicolon delimiter
- tab delimiter
- whitespace delimiter, if implemented
- decimal comma
- unit suffix cleanup
- missing required columns
- invalid numeric values
- no-header column-index mappings
- side value mapping

Tests must not require:

- network access
- Ollama
- API keys
- company parser
- proprietary examples

## Human review checklist

- [ ] The parser is clearly original PoC code.
- [ ] No proprietary parser code or test data is used.
- [ ] Scope is limited to dry-run validation.
- [ ] CLI behavior is clear.
- [ ] Parser output is useful for Stage 5 repair loop.
- [ ] Tests are deterministic.
- [ ] Implementation may begin.
