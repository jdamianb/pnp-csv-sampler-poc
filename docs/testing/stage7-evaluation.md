# Testing: Stage 7 — Evaluation and Dataset Building

## Test Suite

Stage 7 does not add new unit tests. The evaluation harness (`scripts/run-evaluation.sh`) is a shell-based measurement tool, not a test suite.

## Verification

### Stub mode (no LLM required)

```bash
./scripts/run-evaluation.sh
```

Expected result: All metrics rendered, provider shows "stub", report written to `target/evaluation-report.md`.

### Ollama mode

```bash
./scripts/run-evaluation.sh http://localhost:11434 qwen2.5:3b
```

Expected result: All 12 files processed with real LLM, per-file results in `target/eval-outputs/`, reports generated.

## IntelliJ Configurations

| Configuration | Command | Expected |
|---|---|---|
| `Run evaluation (stub)` | `scripts/run-evaluation.sh` | Report generated with stub provider |
| `Run evaluation (Ollama)` | `scripts/run-evaluation.sh http://localhost:11434 qwen2.5:3b` | 12 files processed with Ollama |

## Report Format

### JSON (`target/evaluation-report.json`)

```json
{
  "evaluationDate": "2026-06-28",
  "llmProvider": "ollama",
  "llmModel": "qwen2.5:3b",
  "totalFiles": 12,
  "metrics": {
    "validJsonRate": 91.7,
    "validatorPassRate": 91.7,
    "parserSuccessRate": 66.7,
    "configAccuracyRate": 66.7,
    "repairEffectiveness": "40.0"
  },
  "perFileResults": [],
  "knownFailureCases": []
}
```

### Markdown (`target/evaluation-report.md`)

Markdown report with:
- Summary metrics table
- Per-file breakdown table with icons
- Known failure cases section

## Existing Tests

All 150 existing unit tests continue to pass with no regressions:

```bash
mvn test
```
