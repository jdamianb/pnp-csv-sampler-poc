# Specification: Stage 5 — Repair Loop for Config Correction

## 1. Purpose

Add a bounded repair loop that, when a proposed `PnpImportFormatConfig` fails validation or parser dry-run, automatically sends validation and parser errors back to the LLM for correction. The loop runs up to a configurable maximum number of retries, then exposes the failure clearly if it cannot produce a valid config.

## 2. Scope

This feature extends the existing pipeline with:

- `RepairPromptBuilder` — builds a repair prompt containing the original sample, the broken config, and the errors
- Repair logic integrated into the `detect` CLI command
- `--repair-max N` CLI flag (default: 2)
- `--no-repair` CLI flag to disable the loop
- Repair metadata in the output (number of attempts, whether repaired)
- Stub-aware handling: detect when the LLM cannot improve and stop early

## 3. Out of Scope

- UI for manual config editing (Stage 6)
- Persistence of corrected configs
- Fine-tuning or RAG
- Repairing component data values — only config correction
- Section-aware repair for complex files
- Spring Boot
- Database

## 4. Assumptions

- The existing LLM (stub or real) can produce a config JSON in the expected format
- The existing `PnpImportFormatConfigValidator` produces actionable error messages
- The existing `SimplePnpParser` produces actionable error messages
- The repair prompt is sent to the **same** LLM that produced the original config
- A stub that always returns identical JSON cannot produce a better config on retry

## 5. EARS Requirements

### 5.1 Ubiquitous

```
R01  The system shall implement a bounded repair loop that corrects invalid PnpImportFormatConfig proposals.
R02  The repair loop shall use the same LlmClient that produced the original config.
R03  The repair loop shall produce a valid PnpImportFormatConfig or expose the failure clearly.
R04  The repair loop shall not modify component data — only config fields (delimiter, columns, etc.).
R05  The system shall include a RepairPromptBuilder that assembles repair prompts.
```

### 5.2 Event-driven (When)

```
R06  When detection produces a valid config, the repair loop shall not run — the config shall be output immediately.
R07  When detection produces an invalid config, the repair loop shall build a repair prompt and send it to the LLM.
R08  When a repair attempt produces a valid config, the loop shall stop and output the corrected config.
R09  When a repair attempt produces an invalid config and retries remain, the loop shall build a new repair prompt with updated errors and retry.
R10  When a repair attempt produces an invalid config and no retries remain, the loop shall stop and expose the errors clearly.
R11  When using StubLlmClient, the system shall detect that the stub returns identical JSON and stop after one repair attempt.
```

### 5.3 State-driven (While)

```
R12  While repairing, the repair prompt shall contain: the original CSV sample, the broken config as JSON, and the validation/parser error messages.
R13  While repairing, the system shall track the number of repair attempts.
R14  While detecting with repair enabled, the output shall include repair metadata (number of attempts, whether the config was repaired).
```

### 5.4 Feature

```
R15  The detect CLI command shall accept --repair-max N (default: 2) to set the maximum number of repair attempts.
R16  The detect CLI command shall accept --no-repair to disable the repair loop.
R17  The detect CLI command may use the environment variable PNP_REPAIR_MAX as default for --repair-max.
```

### 5.5 Unwanted behavior (If)

```
R18  If the LLM returns an unparseable response during repair, the system shall count this as a failed repair attempt.
R19  If all repair attempts fail, the system shall output the last config and its errors, not a valid-but-wrong config.
R20  If --no-repair is set, the system shall behave exactly as Stage 2/3 without any repair logic.
```

## 6. Gherkin Acceptance Scenarios

```gherkin
Feature: Repair Loop

  Background:
    Given Stages 1-4 are implemented
    And the detector, validator, and parser exist

  Scenario: Valid config does not trigger repair
    Given an LLM produces a config that passes validation and parser dry-run
    When the detect command runs with default repair settings
    Then the repair loop shall not run
    And the output shall show success
    And the output shall show repairAttempts: 0

  Scenario: Invalid config triggers repair and succeeds
    Given an LLM produces an invalid config (e.g., wrong delimiter)
    And the repair loop is enabled with max retries >= 1
    When the detect command runs
    Then the repair loop shall build a repair prompt with the errors
    And the LLM shall produce a corrected config
    And the output shall show a valid config
    And the output shall show repairAttempts >= 1
    And the output shall show repaired: true

  Scenario: Invalid config triggers repair and fails after max retries
    Given an LLM produces an invalid config
    And the repair loop is enabled with max retries = 2
    And all 2 repair attempts also produce invalid configs
    When the detect command runs
    Then the system shall stop after 2 repair attempts
    And expose the errors clearly
    And exit with non-zero status

  Scenario: StubLlmClient stops repair after 1 attempt
    Given StubLlmClient is used (no real LLM configured)
    And the stub always returns the same JSON
    When the detect command runs
    Then the system shall detect the stub returned identical JSON
    And stop after 1 repair attempt at most
    And expose the errors clearly

  Scenario: --no-repair flag disables the repair loop
    Given an LLM produces an invalid config
    And --no-repair is set on the CLI
    When the detect command runs
    Then the repair loop shall not run
    And the output shall be the raw detection result

  Scenario: --repair-max 0 is equivalent to --no-repair
    Given an LLM produces an invalid config
    And --repair-max 0 is set on the CLI
    When the detect command runs
    Then the repair loop shall not run
    And the output shall be the raw detection result

  Scenario: Repair prompt contains sample, config, and errors
    Given an LLM produces an invalid config
    And the validator/parser produce specific error messages
    When the repair prompt is built
    Then the prompt shall contain the original CSV sample
    And the prompt shall contain the broken config as JSON
    And the prompt shall contain the error messages

  Scenario: Parser errors are included in repair feedback
    Given an LLM produces a config that passes validation but fails parsing
    And the parser produces row-level errors (e.g., "X value 'abc' is not a number")
    When the repair prompt is built
    Then the prompt shall include the parser errors
    And the LLM shall have the opportunity to fix the config

  Scenario: Existing tests still pass
    Given the existing test suite (Stages 1-4)
    When mvn test is run
    Then all existing tests shall pass
    And Stage 3 integration tests shall remain opt-in
```

## 7. Non-functional Requirements

| ID | Requirement |
|---|---|
| NF01 | Language: Java 21 |
| NF02 | Build tool: Maven |
| NF03 | JSON: Jackson 2.x (existing) |
| NF04 | Tests: JUnit 5 |
| NF05 | Spring Boot: not allowed |
| NF06 | All repair tests must be deterministic (use StubLlmClient) |
| NF07 | No additional Maven dependencies |
| NF08 | Repair loop must be bounded (no infinite loops) |
| NF09 | CLI args override environment variables |

## 8. Testability Notes

- `RepairPromptBuilder` can be tested in isolation: give it a sample, config, and errors, verify the output prompt contains all three
- Repair loop logic can be tested with `StubLlmClient`: verify it detects identical JSON and stops early
- Use a `StubLlmClient` that returns different JSON on successive calls to test the success path
- All tests must be deterministic and require no network
- No real LLM calls in unit tests

## 9. IntelliJ Run Configurations for Validation

After implementation, the following IntelliJ run configurations shall be created in `.run/` to validate the repair loop behavior:

| Configuration | Arguments | Expected Behavior |
|---|---|---|
| `Run detect repair enabled` | `detect examples/simple-pnp.csv --repair-max 2` | Repair enabled (or stub fallback if no LLM) |
| `Run detect no repair` | `detect examples/simple-pnp.csv --no-repair` | No repair, same as Stage 2/3 |
| `Run detect repair max 0` | `detect examples/simple-pnp.csv --repair-max 0` | No repair (equivalent to `--no-repair`) |

These configurations allow the reviewer and human to quickly verify that the CLI flags work and the repair loop behaves as expected.

## 10. Human Review Checklist

- [ ] Purpose describes bounded repair loop for config correction
- [ ] Scope limited to repair prompt builder, repair orchestrator, CLI flags
- [ ] Out of scope excludes UI, persistence, fine-tuning, data repair
- [ ] EARS requirements cover valid, invalid, stub, max retries, --no-repair cases
- [ ] Gherkin scenarios cover: valid config (no repair), repair success, repair failure, stub behavior, --no-repair, --repair-max 0, prompt content, parser errors
- [ ] Repair loop is bounded (max retries configurable)
- [ ] Stub-aware: detects identical JSON and stops early
- [ ] No data repair — config fields only
- [ ] Implementation may begin after approval
