## Stage 7: Evaluation and Dataset Building

### Goal

Measure whether the PoC reduces manual import setup by evaluating the LLM-assisted format detection pipeline against the 12 extended example files.

### Metrics

- **Valid JSON rate** — how often does the LLM produce valid JSON?
- **Validator pass rate** — how often does the config pass `PnpImportFormatConfigValidator`?
- **Parser dry-run success rate** — how often does `SimplePnpParser` successfully parse the file?
- **Config accuracy** — how often does the detected config match `expected-configs/`?
- **Repair effectiveness** — how often does the repair loop fix a broken config?
- **Per-file breakdown** — which files succeed/fail and why?

### Existing Infrastructure

- `examples-extended/` — 12 synthetic PnP example files (KiCad, JLCPCB, Altium, OpenPnP, EasyEDA, Diptrace, machine formats, etc.)
- `expected-configs/` — expected `PnpImportFormatConfig` JSON files
- `ExampleOutputGeneratorTest` — generates sample + detect outputs for all examples
- `RepairLoopExampleTest` — runs repair loop on all examples
- `scripts/run-repair-loop.sh` — CLI script to run repair loop on all examples with Ollama

### What Needs to Be Built

1. An evaluation harness that:
   - Runs the full pipeline (detect + repair) on all 12 examples using Ollama
   - Collects metrics per file
   - Produces an evaluation report in JSON and Markdown

2. Documentation of:
   - Known failure cases and patterns
   - Which file formats the LLM handles well/poorly
   - Accuracy metrics summary
   - Final PoC summary

### Inputs

- `examples-extended/` — 12 example files
- `expected-configs/` — 12 expected config files
- `SimplePnpParser` — for parser dry-run validation
- `OllamaLlmClient` — for real LLM detection

### Outputs

- `evaluation-report.json` — machine-readable metrics
- `evaluation-report.md` — human-readable summary
- `target/eval-outputs/` — per-file detailed results

### Constraints

- Must use original, synthetic example files only
- Must not use proprietary company files, code, or data
- Must run with Ollama (opt-in) and fall back to stub for offline runs
- Must not modify existing production code
