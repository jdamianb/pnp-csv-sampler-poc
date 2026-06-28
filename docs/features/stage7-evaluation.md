# Stage 7: Evaluation and Dataset Building

## Summary

An evaluation harness that measures the accuracy and effectiveness of the LLM-assisted PnP format detection pipeline against 12 synthetic example files with known expected configurations.

## Status

✅ Implemented and tested.

## Script

`scripts/run-evaluation.sh`

```bash
# Run with StubLlmClient (no config needed)
./scripts/run-evaluation.sh

# Run with Ollama
./scripts/run-evaluation.sh http://localhost:11434 qwen2.5:3b
```

## Outputs

| Path | Description |
|---|---|
| `target/eval-outputs/` | Per-file result JSONs and stderr logs |
| `target/evaluation-report.json` | Machine-readable metrics in JSON format |
| `target/evaluation-report.md` | Human-readable summary with tables |

## Metrics Tracked

| Metric | Description |
|---|---|
| Valid JSON rate | Percentage of files where LLM response is valid JSON |
| Validator pass rate | Percentage of files where config passes `PnpImportFormatConfigValidator` |
| Parser success rate | Percentage of files where detected config matches expected config structure |
| Config accuracy | Percentage of files where detected config matches `expected-configs/` |
| Repair effectiveness | Percentage of repair attempts that converted a failure to a success |

## Evaluation Results (Ollama, qwen2.5:3b, 2026-06-28)

| Metric | Rate |
|---|---|
| Valid JSON | 91.7% |
| Validator Pass | 91.7% |
| Parser Success | 66.7% |
| Config Accuracy | 66.7% |
| Repair Effectiveness | 40.0% |

## Known Failure Cases

| File | Issue | Root Cause |
|---|---|---|
| `01_kicad_native_ascii_mm.pos` | Whitespace delimiter | LLM proposes comma for space-separated file |
| `03_jlcpcb_cpl_minimal.csv` | Empty column mappings | LLM leaves partNumber/jedec empty |
| `08_machine_semicolon_decimal_comma.csv` | Semicolon delimiter | LLM proposes comma for semicolon file |
| `09_machine_tab_separated_with_metadata.tsv` | Tab delimiter | LLM proposes comma for tab file |

**Pattern**: The qwen2.5:3b model defaults to comma delimiter and does not reliably detect non-comma delimiters.

## IntelliJ Configurations

| Configuration | LLM |
|---|---|
| `Run evaluation (stub)` | StubLlmClient (default, no config needed) |
| `Run evaluation (Ollama)` | Ollama qwen2.5:3b |

## Next Steps

- Test with larger models (e.g., qwen2.5:7b, llama3.2) to improve delimiter detection
- Fine-tune the detection prompt for non-comma delimiters
- Build a labeled dataset for Stage 8 (fine-tuning investigation)
