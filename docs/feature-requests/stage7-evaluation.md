# Stage 7: Evaluation and Dataset Building

## Goal

Measure whether the PoC reduces manual import setup. Produce a quantitative evaluation of the LLM-assisted format detection pipeline against the 12 extended example files.

## Metrics

- **Valid JSON rate** — percentage of files where LLM response is valid JSON
- **Validator pass rate** — percentage of files where config passes `PnpImportFormatConfigValidator`
- **Parser dry-run success rate** — percentage of files where `SimplePnpParser` successfully parses
- **Config accuracy** — percentage of detected configs matching `expected-configs/`
- **Repair effectiveness** — how often repair loop converts a failed config to a successful one
- **Per-file breakdown** — individual results for each of the 12 example files

## Existing Infrastructure

- `examples-extended/` — 12 example files
- `expected-configs/` — 12 expected config files
- `SimplePnpParser` — deterministic parser for validation
- `OllamaLlmClient` — real LLM client
- `scripts/run-repair-loop.sh` — existing repair loop runner

## Deliverables

1. `scripts/run-evaluation.sh` — evaluation harness script
2. `target/eval-outputs/` — per-file detailed results
3. `target/evaluation-report.json` — machine-readable metrics
4. `target/evaluation-report.md` — human-readable summary
5. Updated documentation of known failure cases

## Out of Scope

- Fine-tuning
- UI
- Database persistence
- New example files
- Production deployment
