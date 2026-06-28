# Feature: Stage 5 — Repair Loop for Config Correction

## Summary

A bounded repair loop that, when a proposed `PnpImportFormatConfig` fails validation or parser dry-run, automatically sends validation and parser errors back to the LLM for correction. The loop runs up to a configurable maximum number of retries (default: 2), then exposes the failure clearly if it cannot produce a valid config.

## Status

✅ Implemented, tested, reviewed, and approved (Stage 5).

## Architecture

```
CLI: detect <file> [--repair-max N] [--no-repair]
  │
  ├── If --no-repair or --repair-max 0:
  │     └── Original flow (Detector.detect → output)
  │
  └── Else (repair loop enabled):
        ├── Detector.detect(sample) → DetectionResult
        ├── If valid AND parser succeeds:
        │     └── Output with repaired:false, repairAttempts:0
        ├── Else (repair needed):
        │     ├── Build repair prompt (sample + broken config + errors)
        │     ├── Send to same LLM for corrected config
        │     ├── Parse, validate, parser-check corrected config
        │     ├── If valid → output with repaired:true
        │     ├── If LLM returns identical JSON → stop (stub detected)
        │     └── Loop up to maxRetries times
        └── If exhausted → output errors with repair metadata
```

## Source Files

| File | Lines | Description |
|---|---|---|
| `RepairPromptBuilder.java` | 82 | Builds repair prompt from sample, broken config, and errors |
| `RepairLoop.java` | ~200 | Bounded repair loop orchestrator with stub detection |
| `Main.java` (modified) | — | Added `--repair-max N`, `--no-repair` CLI flags |

## RepairPromptBuilder

Builds a prompt containing:
1. The previously proposed (broken) config as pretty-printed JSON
2. Validation/parser error messages
3. The original CSV sample (same format as the detection prompt)
4. Instructions to fix only config fields (not data)

The prompt reminds the LLM:
> "Do NOT change the file content or component data — only fix the configuration fields
> (delimiter, column mappings, decimal separator, line indexes, units, etc.)."

## RepairLoop Features

| Feature | Status |
|---|---|
| Bounded retries (configurable via `--repair-max`) | ✅ Default: 2 |
| Stub detection (identical JSON → stop early) | ✅ |
| Config validation after each repair | ✅ |
| Parser dry-run after each repair | ✅ |
| Valid config → no repair overhead (0 attempts) | ✅ |
| Repair metadata in output (`repaired`, `repairAttempts`) | ✅ |
| Diagnostic logging to stderr (`[RepairLoop]` prefixed) | ✅ |

## Diagnostic Logging

The `[RepairLoop]` log lines are output to **stderr** to avoid interfering with the JSON result output. Each repair attempt logs:

- `[RepairLoop] Attempt X/Y` — which attempt number
- `[RepairLoop] Errors to fix:` — the specific validation/parser errors
- `[RepairLoop] Repair prompt (first 300 chars):` — excerpt of the repair prompt
- `[RepairLoop] LLM response (first 200 chars):` — excerpt of the LLM's response

When using the `run-repair-loop.sh` script, logs are saved to `target/repair-loop-logs/` for each file.

## CLI Usage

```bash
# Detect with repair enabled (default: max 2 attempts)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv

# Detect with explicit repair limit
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv --repair-max 5

# Detect without repair (original Stage 2/3 behavior)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv --no-repair

# Disable repair via max 0
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv --repair-max 0

# With Ollama and repair
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples-extended/03_jlcpcb_cpl_minimal.csv \
  --llm-provider ollama --llm-url http://localhost:11434 --llm-model qwen2.5:3b \
  --repair-max 2
```

## Output Format

Success (no repair needed):
```json
{
  "repaired": false,
  "repairAttempts": 0,
  "config": { ... }
}
```

Success (repaired):
```json
{
  "repaired": true,
  "repairAttempts": 2,
  "config": { ... }
}
```

Failure (all repair attempts exhausted):
```json
{
  "repaired": false,
  "repairAttempts": 2,
  "errors": [ "Header column '' not found for field 'partNumber'" ],
  "lastConfig": { ... }
}
```

## Script: run-repair-loop.sh

A bash script that runs the repair loop with Ollama on **all** files in `examples-extended/` and compares results against `expected-configs/`.

```bash
bash scripts/run-repair-loop.sh [ollama-url] [ollama-model]
```

Outputs:
- `target/repair-loop-results/` — JSON result files (one per example)
- `target/repair-loop-logs/` — diagnostic log files with `[RepairLoop]` prompt/response output

The summary at the end shows a breakdown:
- Total files
- Valid immediately (matches expected / differs from expected)
- Repaired successfully (matches expected / differs from expected)
- Failed after repair
- Detection failed

## Stub-Aware Behavior

When using `StubLlmClient` (no real LLM configured), the repair loop detects that the LLM returns identical JSON and stops after 1 repair attempt. This avoids wasting retries when no improvement is possible.

## Design Decisions

- **Bounded**: The loop always terminates after `maxRetries` attempts — no infinite loops.
- **Stub detection**: Compares LLM response strings to detect when the LLM cannot improve.
- **Parser verification**: After each repair, the parser dry-run validates the config against actual file content.
- **No data repair**: Only config fields (delimiter, columns, units) are corrected — never component data.
- **Diagnostic logs**: `[RepairLoop]` prefixed lines on stderr allow debugging without breaking JSON output.
