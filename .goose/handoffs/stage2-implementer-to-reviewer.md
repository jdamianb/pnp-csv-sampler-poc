# Handoff: Stage 2 Implementer to Reviewer

## Context

Stage 2 (LLM-assisted PnP Format Detection) implementation is complete following the approved specification at `docs/specs/llm-assisted-pnp-format-detection-spec.md`.

The first implementation uses `StubLlmClient` for deterministic tests. No real LLM calls are introduced.

## Implementation Summary

| Aspect | Detail |
|---|---|
| Language | Java 21 |
| Build tool | Maven 3.8.7 |
| Dependencies | Jackson 2.17.2, JUnit 5.10.3 (unchanged from Stage 1) |
| Package | `com.example.pnp` |
| Tests | 76 total (31 existing + 45 new), all passing |
| Spring Boot | **Not used** |

## Source Files Added

| File | Description |
|---|---|
| `src/main/java/com/example/pnp/ColumnSource.java` | Enum: `HEADER_NAME`, `COLUMN_INDEX` |
| `src/main/java/com/example/pnp/ColumnMapping.java` | Record: `source` + `value` |
| `src/main/java/com/example/pnp/PnpImportFormatConfig.java` | Config model: 14 fields including columns, units, valueMappings |
| `src/main/java/com/example/pnp/LlmClient.java` | Interface: `sendPrompt(String)` |
| `src/main/java/com/example/pnp/StubLlmClient.java` | Deterministic stub: returns fixed JSON |
| `src/main/java/com/example/pnp/FormatDetectionPromptBuilder.java` | Builds LLM prompt from `SampleResult` |
| `src/main/java/com/example/pnp/PnpImportFormatConfigValidator.java` | 12 validation rules |
| `src/main/java/com/example/pnp/Detector.java` | Orchestrator: prompt → LLM → parse → validate |

## Source Files Modified

| File | Change |
|---|---|
| `src/main/java/com/example/pnp/Main.java` | Added `detect <file>` command alongside existing `sample` command |

## Test Files Added

| File | Tests | Scope |
|---|---|---|
| `FormatDetectionPromptBuilderTest.java` | 8 | Prompt content, schema, sample lines inclusion |
| `PnpImportFormatConfigValidatorTest.java` | 21 | All 12 rejection rules + valid config + edge cases |
| `StubLlmClientTest.java` | 4 | Determinism, response fidelity |
| `PnpImportFormatConfigSerializationTest.java` | 3 | JSON round-trip, COLUMN_INDEX deserialization |
| `DetectorTest.java` | 4 | Valid/invalid stub response, validation integration |

## Test Files Modified

| File | Change |
|---|---|
| `MainTest.java` | Added 5 detect CLI tests (+17 existing = 22 total) |

## Validation Rules Implemented

All 12 rules from the spec:

1. `schemaVersion != 1` → reject
2. `confidence < 0 or > 1` → reject
3. `delimiter` blank or null → reject
4. `decimalSeparator` not `.` or `,` → reject
5. `dataStartRowIndex < 0` → reject
6. `dataEndRowIndex` present and < `dataStartRowIndex` → reject
7. `headerRowIndex` present and < 0 → reject
8. Missing X column → reject
9. Missing Y column → reject
10. Missing angle column → reject
11. No identity column (reference, partNumber, jedec) → reject
12. x, y, angle all same COLUMN_INDEX → reject
13. Non-numeric COLUMN_INDEX value → reject

## Architecture

```
CLI (Main.java)
  ├── sample <file>     → Sampler → SampleResult → JSON output
  └── detect <file>     → Sampler → SampleResult
                         → FormatDetectionPromptBuilder → prompt
                         → StubLlmClient (or real LLM in Stage 3)
                         → Detector.parse → PnpImportFormatConfig
                         → PnpImportFormatConfigValidator
                         → JSON output or validation errors
```

## CLI Behavior

```bash
# Existing sampler (backward compat)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/simple-pnp.csv
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar sample examples/simple-pnp.csv

# New detect command (uses StubLlmClient in Stage 2)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv
```

## Verification

```bash
mvn test                    # 76 tests, all pass
mvn clean package           # builds shaded uber-jar
java -jar ... detect examples/simple-pnp.csv   # outputs valid config JSON
java -jar ... detect nonexistent.csv           # exit 1, error to stderr
```

## Scope Compliance

**In scope (all implemented):**
- [x] PnpImportFormatConfig with full schema
- [x] ColumnMapping with HEADER_NAME and COLUMN_INDEX sources
- [x] FormatDetectionPromptBuilder with clear LLM instructions
- [x] LlmClient interface for future extensibility
- [x] StubLlmClient for deterministic tests
- [x] PnpImportFormatConfigValidator with 12+ validation rules
- [x] `detect <file>` CLI command
- [x] 45 deterministic tests, all pass without network

**Out of scope (not implemented):**
- [x] No real LLM calls (Ollama, OpenAI, DeepSeek)
- [x] No Spring Boot
- [x] No database
- [x] No web UI
- [x] No RAG
- [x] No fine-tuning
- [x] No parser replacement
- [x] No component-row normalization
- [x] No parser dry-run integration (Stage 4)
- [x] No repair loop (Stage 5)
- [x] No human review UI (Stage 6)

**Existing behavior preserved:**
- [x] `sample` command unchanged
- [x] All 31 existing tests pass
- [x] Backward compatible: `java -jar ... file.csv` still works

## Reviewer Checklist

- [ ] Code quality — clean, well-structured, follows Java 21 patterns
- [ ] Scope compliance — no out-of-scope features introduced
- [ ] All 76 tests pass
- [ ] Gherkin scenario coverage verified (25 scenarios)
- [ ] Validation behavior: all 12+ rejection rules tested
- [ ] Prompt builder tests verify LLM instructions
- [ ] No network access required for tests
- [ ] StubLlmClient is deterministic
- [ ] Existing sampler tests still pass
- [ ] CLI detect command works
- [ ] No overengineering

## Stop Condition

Reviewer accepts or requests changes. If changes needed, loop back to implementer.
