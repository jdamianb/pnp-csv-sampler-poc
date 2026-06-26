# PnP CSV Sampler — Specification

## 1. Purpose

Build a deterministic Java 21 command-line tool that reads a Pick-and-Place (PnP) CSV file as raw text and produces a JSON sample. The sample contains the total line count plus the first N and last M lines, each with its zero-based index and exact original text.

The sampler exists to provide a reliable, non-parsing input source for a downstream LLM-based CSV parser. The LLM integration is explicitly **not** part of this PoC.

## 2. Scope

The sampler shall:

- Accept a file path as a CLI argument.
- Accept optional numeric overrides for first-line count and last-line count.
- Read the file as raw text using UTF-8 charset.
- Count the total number of lines in the file.
- Extract the first N lines (default 80).
- Extract the last M lines (default 20).
- Preserve each extracted line's zero-based index and raw text exactly.
- Output a well-formed JSON object to stdout.
- Stream the file to handle arbitrarily large inputs without loading the entire contents into memory.

## 3. Out of Scope

The following are **explicitly excluded** from this PoC:

- CSV field parsing or splitting
- Delimiter inference
- Header row detection
- Column mapping
- Unit detection or normalization
- Decimal normalization or formatting
- Line content trimming or transformation of any kind
- LLM API calls
- RAG or vector storage
- Spring Boot
- REST API endpoints
- Web UI or GUI
- Database storage
- Configuration files
- Fine-tuning
- Any form of content analysis or transformation

## 4. Assumptions

- Input files use UTF-8 encoding.
- A line is any sequence of bytes terminated by `\n`, `\r\n`, or end-of-file.
- Lines are indexed starting from zero.
- An empty file has zero lines.
- The system has sufficient heap memory to hold the sampled lines (first N + last M), but not necessarily the entire file.
- The file path argument is always provided; missing argument is an error.
- Negative or zero counts for first/last lines are treated as errors.

## 5. EARS Requirements

### 5.1 Ubiquitous

```
R01  The system shall read the input file as raw text.
R02  The system shall count the total number of lines in the input file.
R03  The system shall assign each line a zero-based index.
R04  The system shall preserve each sampled line's raw text exactly as it appears in the file.
R05  The system shall output the result as a single JSON object to stdout.
R06  The system shall accept a file path as the first command-line argument.
```

### 5.2 When (trigger) — default counts

```
R07  When the file path is provided without --first or --last flags,
     the system shall sample the first 80 lines and the last 20 lines.
```

### 5.3 When (trigger) — custom counts

```
R08  When the --first option is provided with a positive integer,
     the system shall sample that many lines from the beginning of the file.
R09  When the --last option is provided with a positive integer,
     the system shall sample that many lines from the end of the file.
```

### 5.4 Where (condition) — overlapping samples

```
R10  Where the total number of lines is less than or equal to the sum of
     first-lines and last-lines requested, the system shall include every
     line exactly once, with no duplicates.
```

### 5.5 If (condition) — error handling

```
R11  If the file does not exist, the system shall print an error to stderr
     and exit with a non-zero status code.
R12  If the file cannot be read (permissions, I/O error), the system shall
     print an error to stderr and exit with a non-zero status code.
R13  If --first or --last is given a non-positive integer, the system shall
     print an error to stderr and exit with a non-zero status code.
R14  If no file path argument is given, the system shall print usage
     information to stderr and exit with a non-zero status code.
```

### 5.6 While (state) — streaming

```
R15  While reading the file, the system shall not load the entire file
     into memory. The system may stream lines using a ring buffer or
     equivalent strategy for the last-lines sample.
```

### 5.7 JSON output shape

```
R16  The output JSON shall contain exactly three top-level keys:
     "totalLines" (number), "firstLines" (array), "lastLines" (array).
R17  Each element in firstLines and lastLines shall contain exactly two
     keys: "index" (number, zero-based) and "text" (string, raw line content).
```

## 6. Gherkin Acceptance Scenarios

```gherkin
Feature: PnP CSV Sampler

  Background:
    The sampler is invoked from the command line as:
      java ... com.example.pnp.Main <file> [--first N] [--last M]

  Scenario: Default sampling of a file with more lines than the default sample size
    Given a file "example1.csv" with 100 lines
    When the sampler reads the file with default options
    Then the output shall contain "totalLines": 100
    And "firstLines" shall contain 80 elements with indices 0..79
    And "lastLines" shall contain 20 elements with indices 80..99

  Scenario: Custom first and last counts
    Given a file "example1.csv" with 100 lines
    When the sampler reads the file with --first 10 --last 5
    Then the output shall contain "totalLines": 100
    And "firstLines" shall contain 10 elements with indices 0..9
    And "lastLines" shall contain 5 elements with indices 95..99

  Scenario: File smaller than total requested sample size
    Given a file "small.csv" with 5 lines
    When the sampler reads the file with --first 10 --last 5
    Then "firstLines" shall contain 5 elements with indices 0..4
    And "lastLines" shall be empty
    And the union of firstLines and lastLines shall contain every line exactly once

  Scenario: Preserve raw line content exactly
    Given a file "messy.csv" containing lines with leading/trailing whitespace
    When the sampler reads the file
    Then the "text" value of each sampled line shall be byte-identical
      to the corresponding line in the original file

  Scenario: Preserve zero-based indexing
    Given a file "simple.csv" with 1 line
    When the sampler reads the file
    Then the firstLines element shall have "index": 0
    And "totalLines" shall be 1

  Scenario: File not found
    When the sampler is invoked with a non-existent file path
    Then the exit code shall be non-zero
    And an error message shall be written to stderr

  Scenario: Missing file argument
    When the sampler is invoked with no arguments
    Then the exit code shall be non-zero
    And usage information shall be written to stderr

  Scenario: Invalid --first value
    When the sampler is invoked with --first -5
    Then the exit code shall be non-zero
    And an error shall be written to stderr

  Scenario: Output is well-formed JSON
    Given any valid input file
    When the sampler reads the file
    Then the output shall be parseable as valid JSON

  Scenario: Empty file
    Given an empty file "empty.csv"
    When the sampler reads the file
    Then "totalLines" shall be 0
    And "firstLines" shall be an empty array
    And "lastLines" shall be an empty array

  Scenario: File with single line
    Given a file "single.csv" with exactly 1 line
    When the sampler reads the file with --first 1 --last 1
    Then "totalLines" shall be 1
    And "firstLines" shall contain 1 element with index 0
    And "lastLines" shall be empty (the single line is already in firstLines)

  Scenario: Custom only-first
    Given a file with 50 lines
    When the sampler reads the file with --first 5
    Then firstLines shall contain 5 elements
    And lastLines shall use the default of 20

  Scenario: Custom only-last
    Given a file with 50 lines
    When the sampler reads the file with --last 5
    Then firstLines shall use the default of 80 (all 50 lines)
    And lastLines shall contain 5 elements

  Scenario: Streaming large file
    Given a file with 1_000_000 lines
    When the sampler reads the file with --first 80 --last 20
    Then the peak memory usage shall not be proportional to the file size
    And the output shall be correct

  Scenario: DOS line endings (\r\n)
    Given a file "dos.csv" with CRLF line endings
    When the sampler reads the file
    Then the "text" of each line shall include the \r characters
      exactly as they appear in the file

  Scenario: File with no trailing newline
    Given a file "nofinalnl.csv" where the last line has no newline terminator
    When the sampler reads the file
    Then the last line shall be included as a valid line with its correct index

  Scenario: Error on --last 0
    When the sampler is invoked with --last 0
    Then the exit code shall be non-zero
    And an error shall be written to stderr

  Scenario: Error on --first 0
    When the sampler is invoked with --first 0
    Then the exit code shall be non-zero
    And an error shall be written to stderr
```

## 7. Non-functional Requirements

| ID | Requirement | Detail |
|---|---|---|
| NF01 | Language | Java 21 |
| NF02 | Build tool | Maven |
| NF03 | Dependencies | Jackson for JSON serialization, JUnit 5 for testing |
| NF04 | Spring Boot | Not allowed |
| NF05 | CLI | Single executable Main class with `main(String[])` |
| NF06 | Streaming | Must stream file content; memory proportional to sample size, not file size |
| NF07 | Encoding | UTF-8 |
| NF08 | Determinism | Same input file always produces identical output |
| NF09 | Packaging | `mvn clean package` produces an executable JAR |
| NF10 | Performance | Should handle files of any size (tested up to 10M lines) |

## 8. Testability Notes

- The implementation is fully testable without a filesystem: a `Sampler` class should accept a `Reader` or `InputStream`, allowing tests to provide string content directly.
- The CLI entry point (`Main`) should be a thin wrapper that resolves the file to a `Reader` and delegates to the sampler.
- JSON output can be verified by parsing with Jackson and inspecting the POJO.
- Gherkin scenarios in Section 6 can be covered with JUnit 5 parameterized tests — Cucumber is optional.
- The streaming constraint (NF06) must be verified by inspecting the source code for an in-memory list of all lines (which is prohibited).

## 9. Human Review Checklist

- [ ] All 9 sections present (Purpose, Scope, Out of Scope, Assumptions, EARS, Gherkin, Non-functional, Testability, Review checklist)
- [ ] No requirements for CSV parsing, delimiter inference, header detection, column mapping, or unit normalization
- [ ] No requirements for Spring Boot, REST, LLM, RAG, UI, database, or fine-tuning
- [ ] EARS patterns use at least Ubiquitous, When, Where, If, While styles
- [ ] Gherkin scenarios cover: normal case, custom counts, small file, large file, empty file, file not found, missing argument, invalid argument, raw content preservation, zero-based indexing, CRLF endings, no trailing newline, non-positive counts
- [ ] Gherkin scenarios explicitly test that `--first 0` and `--last 0` are errors
- [ ] Streaming requirement is clearly stated
- [ ] The spec is implementation-neutral (no Java-specific details leaked from this section into the requirements)
