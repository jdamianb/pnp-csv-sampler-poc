# Specification: Stage 4 — Simple Open PnP Parser Dry-run

## 1. Purpose

Implement a small, original, deterministic CSV/TSV parser that consumes a `PnpImportFormatConfig` and an input file, parses the rows according to the config, and produces a `PnpParseDryRunReport`.

This parser serves as the dry-run authority for the public PoC. It validates whether a proposed import configuration can actually parse the file.

## 2. Scope

This feature extends the existing pipeline with:

- `PnpPlacement` — normalized placement row DTO
- `PnpParseDryRunReport` — dry-run result DTO
- `SimplePnpParser` — deterministic parser that applies config rules to rows
- `parse <file> <config-json>` CLI command
- Support for comma, semicolon, tab, and whitespace delimiters
- Support for `HEADER_NAME` and `COLUMN_INDEX` column mappings
- Support for `.` and `,` decimal separators
- Support for unit suffix stripping (e.g., `mm`)
- Support for side value mappings
- Row-level error reporting
- Sample normalized placement rows in the report

## 3. Out of Scope

The following are **explicitly excluded** from Stage 4:

- Proprietary company parser code, behavior, test data, or documentation
- Production-grade parser
- Full CAD/machine format support
- Section-aware parsing for complex multi-section files
- Repair loop (Stage 5)
- UI or human review flow (Stage 6)
- Database or persistence
- RAG or fine-tuning
- LLM-based row parsing (every row must be parsed deterministically)
- `detect-and-parse` combined command (deferred unless explicitly approved)
- Spring Boot
- Any additional dependencies beyond Jackson and JUnit 5

## 4. Assumptions

- `PnpImportFormatConfig` with validated `ColumnMapping`, `ColumnSource`, delimiter, decimal separator, etc. is available
- `PnpImportFormatConfigValidator` exists and validates configs before parsing
- Input files are small enough to read fully into memory (PoC scope)
- Files use UTF-8 encoding
- The parser is a PoC component, not a replacement for a production parser
- All test data will be synthetic — no proprietary or customer files

## 5. EARS Requirements

### 5.1 Ubiquitous

```
R01  The system shall implement a deterministic parser that consumes a PnpImportFormatConfig and an input file.
R02  The parser shall produce a PnpParseDryRunReport.
R03  The parser shall not call any LLM, API, or external service.
R04  The parser shall not use proprietary company parser code, behavior, or data.
R05  The parser shall validate that all required fields (X, Y, angle) are numeric.
R06  The parser shall support comma (`,`), semicolon (`;`), tab (`\t`), and WHITESPACE delimiters.
R06a When the delimiter is WHITESPACE, the parser shall split on one or more consecutive spaces or tabs (variable-width whitespace), not on single spaces.
R07  The parser shall support HEADER_NAME and COLUMN_INDEX mapping sources.
R08  The parser shall support dot (.) and comma (,) as decimal separators.
```

### 5.2 Event-driven (When)

```
R09  When the user runs "parse <file> <config-json>", the system shall read the file and config, run the parser, and print the dry-run report as pretty JSON.
R10  When a row has a non-numeric X, Y, or angle value, the parser shall reject that row and include a row-level error.
R11  When a row lacks a required column value (reference, partNumber, or jedec depending on config), the parser shall include a warning for that row.
R12  When side mapping is configured, the parser shall apply value mappings to the side column.
R13  When a decimal separator is ",", the parser shall normalize the decimal before numeric parsing.
R14  When the config has unit suffixes such as "mm", the parser shall strip the suffix before numeric parsing.
R15  When a row is missing a mapped column entirely, the parser shall include a row-level error.
```

### 5.3 State-driven (While)

```
R16  While parsing, the parser shall apply linesToIgnore before processing data rows.
R17  While parsing, the parser shall honor dataStartRowIndex and dataEndRowIndex bounds.
R18  While parsing, the parser shall resolve column positions by HEADER_NAME using the header row, or by COLUMN_INDEX directly.
R19  While the report is produced, the samplePlacements field shall contain at most the first 10 normalized placement rows.
```

### 5.4 Unwanted behavior (If)

```
R20  If the delimiter is unrecognized, the system shall print an error and exit with code 1.
R21  If the config JSON file does not exist or is unparseable, the system shall print an error and exit with code 1.
R22  If the config fails validation, the system shall print validation errors and exit with code 1.
R23  If the input file is empty or has no candidate rows after applying bounds, the report shall have zero parsed rows and a clear warning.
R24  If the config has a WHITESPACE delimiter, the parser shall split on one or more consecutive spaces or tabs (regex: `\s+`), treating them as a single delimiter, and skip leading/trailing whitespace on each row.
```

## 6. Gherkin Acceptance Scenarios

```gherkin
Feature: Simple Open PnP Parser Dry-run

  Background:
    Given the PnpImportFormatConfig model and validator exist
    And the parser is deterministic and makes no LLM calls

  Scenario: Parse comma-delimited file
    Given a comma-delimited PnP file with header and 4 data rows
    And a valid PnpImportFormatConfig with comma delimiter and HEADER_NAME mappings
    When the user runs the parse command
    Then the report shall show success: true
    And the report shall show 4 parsed rows
    And the report shall show 0 rejected rows

  Scenario: Parse semicolon-delimited file with decimal comma
    Given a semicolon-delimited PnP file with European decimal comma
    And a valid PnpImportFormatConfig with semicolon delimiter and comma decimal separator
    When the user runs the parse command
    Then the report shall show success: true
    And all X/Y values shall be parsed as correct decimal numbers

  Scenario: Parse tab-delimited file
    Given a tab-delimited PnP file
    And a valid PnpImportFormatConfig with tab delimiter
    When the user runs the parse command
    Then the report shall show success: true

  Scenario: Parse whitespace-delimited file with variable-width spaces
    Given a whitespace-delimited PnP file (.pos format) with columns separated by multiple spaces
    And a valid PnpImportFormatConfig with WHITESPACE delimiter
    When the user runs the parse command
    Then the report shall show success: true
    And the parser shall correctly split columns on the variable-width whitespace
    And all X/Y values shall be parsed as correct numbers

  Scenario: Parse with COLUMN_INDEX mappings
    Given a PnP file without a header row
    And a valid PnpImportFormatConfig using COLUMN_INDEX mappings
    When the user runs the parse command
    Then the report shall show success: true
    And all placement rows shall have correct values

  Scenario: Non-numeric X value produces row-level error
    Given a PnP file where row X column contains "abc"
    And a valid PnpImportFormatConfig
    When the user runs the parse command
    Then the report shall show success: false
    And the report shall include a row-level error for the non-numeric X

  Scenario: Config file not found
    Given a nonexistent config JSON path
    When the user runs the parse command
    Then the system shall exit with code 1
    And print an error message

  Scenario: Invalid config JSON
    Given a config JSON file with invalid content
    When the user runs the parse command
    Then the system shall exit with code 1
    And print an error message

  Scenario: Config fails validation
    Given a config JSON that fails the PnpImportFormatConfigValidator
    When the user runs the parse command
    Then the system shall print validation errors
    And exit with code 1

  Scenario: Apply linesToIgnore
    Given a PnP file with 3 comment lines before the header
    And a PnpImportFormatConfig with linesToIgnore [0, 1, 2]
    When the user runs the parse command
    Then the parser shall skip lines 0, 1, and 2
    And parse rows correctly from the header row

  Scenario: Apply dataStartRowIndex and dataEndRowIndex
    Given a PnP file with extra lines after the data
    And a PnpImportFormatConfig with dataEndRowIndex set
    When the user runs the parse command
    Then the parser shall stop at dataEndRowIndex

  Scenario: Unit suffix stripping
    Given a PnP file with X values like "12.3mm"
    And a valid PnpImportFormatConfig
    When the user runs the parse command
    Then the X values shall be parsed as numeric 12.3

  Scenario: Side value mapping
    Given a PnP file with side values "top" and "bottom"
    And a PnpImportFormatConfig with valueMappings { "top": "Top", "bottom": "Bottom" }
    When the user runs the parse command
    Then the side values shall be mapped to "Top" and "Bottom"

  Scenario: Empty file
    Given an empty PnP file
    And a valid PnpImportFormatConfig
    When the user runs the parse command
    Then the report shall show 0 parsed rows
    And the report shall include a warning

  Scenario: Unrecognized delimiter
    Given a PnpImportFormatConfig with delimiter "pipe"
    When the user runs the parse command
    Then the system shall exit with code 1
    And print an error message about unrecognized delimiter

  Scenario: Existing tests still pass
    Given the existing test suite
    When mvn test is run
    Then all existing Stage 1 and Stage 2 tests shall pass
    And all Stage 3 Ollama tests shall remain opt-in
```

## 7. Non-functional Requirements

| ID | Requirement |
|---|---|
| NF01 | Language: Java 21 |
| NF02 | Build tool: Maven |
| NF03 | JSON: Jackson 2.x (existing dependency) |
| NF04 | Tests: JUnit 5 |
| NF05 | Spring Boot: not allowed |
| NF06 | All parser tests must be deterministic and require no network |
| NF07 | All parser tests must use synthetic examples only |
| NF08 | Parser must accept a `Reader` for testability (no filesystem dependency) |
| NF09 | Reports must be serializable as JSON with Jackson |
| NF10 | No additional Maven dependencies beyond what Stage 1-3 already use |

## 8. Testability Notes

- The parser should accept a `Reader` for the input file, allowing tests to provide `StringReader` directly
- Config can be provided as a `PnpImportFormatConfig` object directly in tests
- All test data must be synthetic strings — no filesystem needed
- Each delimiter type should have its own test:
  - Comma (`,`)
  - Semicolon (`;`)
  - Tab (`\t`)
  - Whitespace (`WHITESPACE`) — splitting on one or more consecutive spaces
- Each mapping source should have its own test:
  - `HEADER_NAME` (with header row)
  - `COLUMN_INDEX` (without header row)
- Error paths to test:
  - Non-numeric X, Y, or angle
  - Missing column
  - Empty file
  - Config file not found
  - Invalid config JSON
  - Config failing validator
  - Unrecognized delimiter

## 9. Human Review Checklist

- [ ] Purpose states simple original parser for dry-run config validation
- [ ] Scope limited to parser, placements DTO, report DTO, `parse` CLI command
- [ ] Out of scope excludes proprietary code, production-grade, repair loop, UI
- [ ] All synthetic examples — no proprietary or customer data
- [ ] EARS requirements cover all supported delimiters, mapping sources, error cases
- [ ] Gherkin scenarios cover: 4 delimiters, 2 mapping sources, numeric errors, config errors, bounds, empty file
- [ ] No LLM calls during parsing
- [ ] No Spring Boot
- [ ] Parser is deterministic
- [ ] Implementation may begin after approval
