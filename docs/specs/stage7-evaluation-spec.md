# Specification: Stage 7 — Evaluation and Dataset Building

## 1. Purpose

Produce a quantitative evaluation report that measures how well the LLM-assisted PnP format detection pipeline reduces manual import setup compared to the fully manual process.

## 2. Scope

**In scope:**
- Evaluation harness script that runs the full pipeline on all 12 example files
- Collection of metrics: valid JSON rate, validator pass rate, parser success rate, config accuracy
- Repair loop effectiveness measurement
- Per-file detailed results
- Evaluation report in JSON and Markdown formats
- Documentation of known failure cases and patterns

**Out of scope:**
- Creating new example files beyond the existing 12
- Fine-tuning or model changes
- UI or database
- Production deployment
- Changes to existing production code

## 3. EARS Requirements

### 3.1 Ubiquitous requirements

| ID | Requirement |
|---|---|
| R01 | The evaluation harness SHALL run the detect pipeline on each file in `examples-extended/` using the configured LLM provider |
| R02 | The evaluation harness SHALL collect per-file metrics including success/failure status, config JSON, and errors |
| R03 | The evaluation harness SHALL compare the detected config against the expected config in `expected-configs/` when available |
| R04 | The evaluation harness SHALL produce a machine-readable report (`evaluation-report.json`) with all metrics |
| R05 | The evaluation harness SHALL produce a human-readable report (`evaluation-report.md`) with formatted results |
| R06 | The evaluation harness SHALL output per-file detailed results to `target/eval-outputs/` |

### 3.2 Event-driven requirements

| ID | Requirement |
|---|---|
| R07 | WHEN the LLM response is not valid JSON, THEN the error SHALL be recorded as a "valid JSON" failure |
| R08 | WHEN the LLM response is valid JSON but fails validation, THEN the error SHALL be recorded as a "validator" failure |
| R09 | WHEN the config passes validation but the parser fails, THEN the error SHALL be recorded as a "parser dry-run" failure |
| R10 | WHEN the config passes both validation and parser, THEN it SHALL be compared against the expected config |
| R11 | WHEN the repair loop is enabled and the initial config fails, THEN the repair outcome SHALL be recorded |

### 3.3 State-driven requirements

| ID | Requirement |
|---|---|
| R12 | WHILE running the evaluation, the harness SHALL track cumulative counts for each metric |
| R13 | WHILE running the evaluation, the harness SHALL log progress per file to stdout |

### 3.4 Unwanted behavior requirements

| ID | Requirement |
|---|---|
| R14 | IF the LLM is unreachable or returns an error, THEN the harness SHALL record the failure and continue to the next file |
| R15 | IF no expected config exists for a file, THEN the accuracy comparison SHALL be skipped (not counted as failure) |
| R16 | IF the evaluation is run without a real LLM, THEN the harness SHALL use the StubLlmClient and note this in the report |

### 3.5 Feature requirements

| ID | Requirement |
|---|---|
| R17 | The evaluation report SHALL include the valid JSON rate as a percentage |
| R18 | The evaluation report SHALL include the validator pass rate as a percentage |
| R19 | The evaluation report SHALL include the parser dry-run success rate as a percentage |
| R20 | The evaluation report SHALL include the config accuracy rate as a percentage |
| R21 | The evaluation report SHALL include the repair loop effectiveness rate (failures converted to successes) |
| R22 | The evaluation report SHALL include a per-file breakdown table |
| R23 | The evaluation report SHALL include a list of known failure cases with descriptions |
| R24 | The evaluation report SHALL include the total number of files evaluated |

## 4. Gherkin Scenarios

### 4.1 All files succeed
```gherkin
Scenario: All files pass detection, validation, and parser
  Given 12 example files in examples-extended/
  And an LLM that returns valid configs for all files
  When the evaluation harness runs on all files
  Then valid JSON rate SHALL be 100%
  And validator pass rate SHALL be 100%
  And parser success rate SHALL be 100%
```

### 4.2 Some files fail
```gherkin
Scenario: Some files produce invalid JSON
  Given 12 example files in examples-extended/
  And an LLM that returns valid JSON for 10 files and invalid JSON for 2 files
  When the evaluation harness runs on all files
  Then valid JSON rate SHALL be 83%
  And the report SHALL list the 2 files with JSON errors
```

### 4.3 Repair loop effectiveness
```gherkin
Scenario: Repair loop fixes some failures
  Given 12 example files in examples-extended/
  And an LLM whose initial config fails for 4 files
  And the repair loop fixes 2 of those 4 failures
  When the evaluation harness runs on all files with --repair-max 2
  Then parser success rate SHALL be 83%
  And repair effectiveness SHALL be 50%
  And the report SHALL show repair attempts per file
```

### 4.4 Expected config comparison
```gherkin
Scenario: Detected config matches expected config
  Given 12 example files in examples-extended/
  And 12 expected config files in expected-configs/
  When the evaluation harness runs on all files
  Then the report SHALL include accuracy comparison for each file
  And the report SHALL show which fields differed
```

### 4.5 Missing expected config
```gherkin
Scenario: Expected config is missing for some files
  Given 12 example files in examples-extended/
  And only 10 expected config files
  When the evaluation harness runs on all files
  Then the report SHALL compare accuracy for 10 files
  And the remaining 2 files SHALL be marked as "no expected config"
```

### 4.6 LLM unreachable
```gherkin
Scenario: LLM is unreachable
  Given 12 example files in examples-extended/
  And an LLM that is not running
  When the evaluation harness runs with a real LLM provider
  Then the harness SHALL record connection errors for all files
  And the report SHALL note LLM availability issues
```

### 4.7 Stub fallback
```gherkin
Scenario: Evaluation runs without a real LLM
  Given 12 example files in examples-extended/
  And no LLM provider configured
  When the evaluation harness runs
  Then it SHALL use StubLlmClient
  And the report SHALL note that results are from stub, not real LLM
```

## 5. Evaluation Report Format

### JSON Report (`evaluation-report.json`)
```json
{
  "evaluationDate": "2026-06-28",
  "llmProvider": "ollama",
  "llmModel": "qwen2.5:3b",
  "totalFiles": 12,
  "metrics": {
    "validJsonRate": 83.3,
    "validatorPassRate": 75.0,
    "parserSuccessRate": 66.7,
    "configAccuracyRate": 50.0,
    "repairEffectiveness": 40.0
  },
  "perFileResults": [
    {
      "file": "01_kicad_native_ascii_mm.pos",
      "validJson": true,
      "validatorPassed": true,
      "parserSuccess": true,
      "matchesExpected": true,
      "repairAttempts": 0,
      "wasRepaired": false,
      "errors": []
    }
  ],
  "knownFailureCases": [
    {
      "file": "08_machine_semicolon.csv",
      "issue": "LLM mismatches delimiter ';' for comma",
      "resolution": "Requires better delimiter detection prompt"
    }
  ],
  "repairStats": {
    "filesRequiringRepair": 4,
    "repairSuccessful": 2,
    "repairFailed": 2,
    "repairEffectiveness": 50.0
  }
}
```

### Markdown Report (`evaluation-report.md`)
```markdown
# PnP Format Detection Evaluation Report

## Summary
- **Date**: 2026-06-28
- **LLM Provider**: ollama
- **LLM Model**: qwen2.5:3b
- **Total Files**: 12

## Metrics
| Metric | Rate |
|---|---|
| Valid JSON | 83.3% |
| Validator Pass | 75.0% |
| Parser Success | 66.7% |
| Config Accuracy | 50.0% |
| Repair Effectiveness | 40.0% |

## Per-File Breakdown
| File | JSON | Validator | Parser | Expected | Repaired |
|---|---|---|---|---|---|
| 01_kicad... | ✅ | ✅ | ✅ | ✅ | — |
| 02_kicad_csv... | ✅ | ✅ | ✅ | ✅ | — |
...

## Known Failure Cases
- **08_machine_semicolon.csv**: ...
- **09_machine_tab.tsv**: ...
```

## 6. IntelliJ Run Configurations

After implementation, the following run configurations SHALL be added:

| Configuration | Purpose |
|---|---|
| `Run evaluation (stub)` | Runs evaluation with StubLlmClient (default) |
| `Run evaluation (Ollama)` | Runs evaluation with Ollama |
| `Run evaluation (Ollama) with repair` | Runs evaluation with Ollama and repair loop |

## 7. Human Review Checklist

- [ ] Evaluation harness produces per-file results
- [ ] JSON report contains all required metrics
- [ ] Markdown report is readable and contains summary + per-file breakdown
- [ ] Hublless without real LLM (uses StubLlmClient)
- [ ] Runs correctly with Ollama
- [ ] Known failure cases are documented
- [ ] No changes to existing production code
- [ ] No proprietary files or data used
