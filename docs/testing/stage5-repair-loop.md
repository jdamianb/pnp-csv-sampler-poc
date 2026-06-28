# Testing: Stage 5 — Repair Loop for Config Correction

## Test Suite

| Suite | File | Tests | Scope |
|---|---|---|---|
| RepairPromptBuilderTest | `RepairPromptBuilderTest.java` | 7 | Repair prompt content |
| RepairLoopTest | `RepairLoopTest.java` | 6 | Repair loop behavior |
| MainTest (Stage 5 additions) | `MainTest.java` | 4 | CLI: `--repair-max`, `--no-repair` |

## Test Coverage

### Prompt Builder

| Scenario | Test |
|---|---|
| Prompt contains sample lines | `promptContainsSampleLines` |
| Prompt contains broken config JSON | `promptContainsBrokenConfig` |
| Prompt contains error messages | `promptContainsErrors` |
| Prompt contains correction instructions | `promptContainsCorrectionInstructions` |
| Empty errors list shows placeholder | `emptyErrorsProducesPlaceholder` |
| Null errors list shows placeholder | `nullErrorsProducesPlaceholder` |
| Prompt asks for JSON only (no explanation) | `promptDoesNotContainExplanationInstruction` |

### Repair Loop

| Scenario | Test |
|---|---|
| Valid config does not trigger repair | `validConfigDoesNotTriggerRepair` |
| Invalid config repairs successfully | `invalidConfigRepairsSuccessfully` |
| Stub returns identical JSON → stops early | `stubReturnsIdenticalJson_stopsEarly` |
| Max retries exhausted → failure | `maxRetriesExhausted_returnsFailure` |
| Zero max retries = no repair | `zeroMaxRetries_noRepair` |
| Config passes validation but fails parser → triggers repair | `configPassesValidationButFailsParser_triggersRepair` |

### CLI (MainTest)

| Scenario | Test |
|---|---|
| `--no-repair` works | `detectWithNoRepairFlag` |
| `--repair-max 0` works (equivalent to --no-repair) | `detectWithRepairMaxZero` |
| `--repair-max -1` rejected | `detectWithRepairMaxNegative` |
| `--repair-max abc` rejected | `detectWithRepairMaxNonNumeric` |

## Running Tests

```bash
# All tests (Stage 1-5)
mvn test

# Stage 5 repair tests
mvn test -Dtest=RepairPromptBuilderTest,RepairLoopTest

# Repair example outputs (stub)
mvn test -Dtest=RepairLoopExampleTest

# Repair example outputs with Ollama
mvn test -Dtest=RepairLoopExampleTest -Dllm.integration=true

# Build and test
mvn clean test
```

## Full Test Suite

**149 tests total** (146 deterministic, 3 skipped = Stage 3 integration):

```bash
mvn test                          # All 149 tests
mvn test -Dllm.integration=true   # Include Ollama integration tests
```
