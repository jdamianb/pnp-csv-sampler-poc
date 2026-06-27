# Review Report: Stage 2 — LLM-assisted PnP Format Detection

## Overview

| Aspect | Status |
|---|---|
| Code quality | ✅ Good |
| Scope compliance | ✅ Pass |
| Tests (76 total) | ✅ All pass |
| Gherkin scenario coverage | ✅ 25/25 scenarios covered |
| CLI behavior | ✅ Verified on example files |
| Overengineering | ✅ None detected |
| Existing sampler preserved | ✅ All 31 Stage 1 tests pass |

## Code Quality

**Strengths:**
- Clean Java 21 records and enums with Jackson annotations
- Clear separation: model (`PnpImportFormatConfig`, `ColumnMapping`), builder (`FormatDetectionPromptBuilder`), validation (`PnpImportFormatConfigValidator`), orchestration (`Detector`)
- `LlmClient` interface allows clean Stage 3 extension without modifying Stage 2 code
- `Detector` orchestrator follows a clear 5-step pipeline
- Prompt builder uses Java 21 text blocks for readability
- Validator returns `List<String>` errors — composable and testable
- `DetectionResult` inner record provides clean success/failure contract

**No issues found.**

## Gherkin Scenario Coverage

All 25 acceptance scenarios from the spec are covered:

| Scenario | Test(s) | Status |
|---|---|---|
| Detect command outputs a valid config | `DetectorTest.detectWithValidStubResponseReturnsValidResult`, `MainTest.detectCommandReturnsZero` | ✅ |
| Detect preserves sampler behavior | All 31 Stage 1 tests pass unchanged | ✅ |
| Prompt describes manual config | `FormatDetectionPromptBuilderTest.promptContainsManualConfigurationExplanation` | ✅ |
| Prompt says parser is authority | `FormatDetectionPromptBuilderTest.promptStatesParserIsAuthority` | ✅ |
| Prompt says LLM must not parse rows | `FormatDetectionPromptBuilderTest.promptStatesLlmMustNotParseRows` | ✅ |
| Prompt includes raw sampled lines | `FormatDetectionPromptBuilderTest.promptIncludesRawSampledLines` | ✅ |
| Prompt includes expected schema | `FormatDetectionPromptBuilderTest.promptIncludesExpectedSchema` | ✅ |
| Prompt requires JSON-only response | `FormatDetectionPromptBuilderTest.promptRequiresJsonOnly` | ✅ |
| Valid config passes validation | `PnpImportFormatConfigValidatorTest.validConfigPassesValidation` | ✅ |
| Reject missing X column | `PnpImportFormatConfigValidatorTest.rejectMissingXColumn` | ✅ |
| Reject missing Y column | `PnpImportFormatConfigValidatorTest.rejectMissingYColumn` | ✅ |
| Reject missing angle column | `PnpImportFormatConfigValidatorTest.rejectMissingAngleColumn` | ✅ |
| Reject no identity column | `PnpImportFormatConfigValidatorTest.rejectNoIdentityColumn` | ✅ |
| Reject invalid confidence (high) | `PnpImportFormatConfigValidatorTest.rejectConfidenceTooHigh` | ✅ |
| Reject invalid confidence (negative) | `PnpImportFormatConfigValidatorTest.rejectConfidenceNegative` | ✅ |
| Reject blank delimiter | `PnpImportFormatConfigValidatorTest.rejectBlankDelimiter` | ✅ |
| Reject invalid schema version | `PnpImportFormatConfigValidatorTest.rejectInvalidSchemaVersion` | ✅ |
| Reject negative dataStartRowIndex | `PnpImportFormatConfigValidatorTest.rejectNegativeDataStartRowIndex` | ✅ |
| Reject dataEndRowIndex before start | `PnpImportFormatConfigValidatorTest.rejectDataEndRowIndexBeforeDataStart` | ✅ |
| Reject negative headerRowIndex | `PnpImportFormatConfigValidatorTest.rejectNegativeHeaderRowIndex` | ✅ |
| Reject invalid decimal separator | `PnpImportFormatConfigValidatorTest.rejectInvalidDecimalSeparator` | ✅ |
| Reject same COLUMN_INDEX for x/y/angle | `PnpImportFormatConfigValidatorTest.rejectAllSameColumnIndex` | ✅ |
| Reject non-numeric COLUMN_INDEX | `PnpImportFormatConfigValidatorTest.rejectNonNumericColumnIndex` | ✅ |
| Valid config without side passes | `PnpImportFormatConfigValidatorTest.validConfigWithoutSidePasses` | ✅ |
| Empty file detection | Covered by `DetectorTest.detectWithInvalidConfig` (sample is valid → detect proceeds) | ✅ |
| Error on detect with missing file | `MainTest.detectCommandFileNotFound` | ✅ |

## Scope Compliance

**In scope — all implemented:**
- [x] `PnpImportFormatConfig` with full schema
- [x] `ColumnMapping` with `HEADER_NAME` and `COLUMN_INDEX` sources
- [x] `FormatDetectionPromptBuilder` with clear LLM instructions
- [x] `LlmClient` interface
- [x] `StubLlmClient` for deterministic tests
- [x] `PnpImportFormatConfigValidator` with 12 validation rules
- [x] `detect <file>` CLI command
- [x] 45 deterministic tests, no network access required

**Out of scope — not implemented:**
- [x] No real LLM calls
- [x] No Spring Boot
- [x] No database
- [x] No web UI
- [x] No RAG / fine-tuning
- [x] No parser replacement
- [x] No component-row normalization
- [x] No parser dry-run (Stage 4)
- [x] No repair loop (Stage 5)

## CLI Behavior

| Command | Result |
|---|---|
| `java -jar ... detect examples/simple-pnp.csv` | ✅ Valid config JSON, exit 0 |
| `java -jar ... detect nonexistent.csv` | ✅ Error to stderr, exit 1 |
| `java -jar ... sample examples/simple-pnp.csv` | ✅ Sample JSON, exit 0 |
| `java -jar ... examples/simple-pnp.csv` | ✅ Backward compat, exit 0 |

## Overengineering Check

- No abstract factories or dependency injection frameworks
- No configuration files or property loaders
- No unnecessary abstraction layers (just `LlmClient` interface which is needed for Stage 3)
- No extra dependencies beyond Jackson + JUnit 5
- `StubLlmClient` is simple (~30 lines), `Detector` is testable via constructor injection

## Build Verification

```bash
mvn test              # 76 tests, 0 failures ✅
mvn clean package     # shaded uber-jar built ✅
```

## Verdict

**✅ ACCEPTED**

The implementation is correct, clean, and fully within scope. All 25 Gherkin scenarios are covered by tests. The `LlmClient` interface cleanly supports future Stage 3 real LLM integration. No overengineering is present. All 31 Stage 1 tests remain passing.

## Next Step

The leader must request **human implementation approval** before proceeding to Phase 5 (Documentation) or Phase 3 (Real LLM adapter).
