# Feature Request: Stage 5 — Repair Loop for Config Correction

## Context

Stages 1-4 are complete:

- Stage 1 samples the file
- Stage 2 proposes a `PnpImportFormatConfig` (with stub)
- Stage 3 adds real Ollama LLM support
- Stage 4 provides a deterministic parser that validates whether a config actually works

Currently, if the LLM proposes a config that fails validation or parsing, the user must manually fix it.

Stage 5 adds a **bounded repair loop** that automatically sends validation and parser errors back to the LLM for correction.

## Goal

When detection fails, instead of immediately showing errors to the user, automatically retry with a repair prompt up to N times (default: 2).

```
LLM proposed config
  ↓
Validator/parser dry-run error
  ↓
Repair prompt (original sample + errors + broken config)
  ↓
LLM proposes corrected config
  ↓
Validate + parse again
  ↓
If still failing, loop (max 2-3 retries)
  ↓
If still broken → expose failure clearly to user
```

## Key design decisions

### The repair prompt builder

Create a new component (e.g., `RepairPromptBuilder`) that assembles a repair prompt from:

1. The original CSV sample (already available from Stage 1)
2. The previously proposed (broken) config
3. The validation errors (from `PnpImportFormatConfigValidator`)
4. The parser errors (from `SimplePnpParser.parse()`)
5. A clear instruction: "Fix the config to resolve these errors"

### Where the repair fits

The repair loop wraps around the existing `Detector`. When `Detector.detect()` returns an invalid result, instead of immediately showing errors:

1. Build a repair prompt
2. Send it to the same LLM (or stub) for a corrected config
3. Parse and validate the corrected config
4. If still invalid and retries remain → loop
5. If max retries reached → show errors and stop

### Bounded retries

Default: 2 retries (so 1 original attempt + up to 2 repairs = 3 total LLM calls).

Configurable via CLI flag `--repair-max N` or environment variable `PNP_REPAIR_MAX`.

### Stub behavior

When using `StubLlmClient`, the repair loop should detect that the stub always returns the same config and stop after one repair attempt (since the stub won't produce a different config).

### CLI changes

- `detect` command gains `--repair-max N` flag (default: 2)
- When repair loop is enabled, the output includes repair metadata:
  - Number of repair attempts
  - Whether the config was repaired
  - The final config (always valid)
- `--no-repair` flag to disable the loop (for debugging)

### Integration with existing pipeline

The existing `Detector` already handles: prompt → LLM → parse → validate. The repair loop wraps this at a higher level:

```
DetectService.repairDetect(sample, llmClient):
  config = detector.detect(sample)  // original attempt
  for i in 1..maxRetries:
    if config is valid AND parser succeeds:
      return (config, repaired=false)
    // Build repair prompt with errors
    repairPrompt = repairPromptBuilder.build(sample, config, errors)
    config = llmClient.sendPrompt(repairPrompt)  // corrected attempt
  // Max retries reached
  return (config, repaired=true, errors)
```

## Out of scope

- UI for manual config editing (Stage 6)
- Persistence of corrected configs
- Fine-tuning or RAG
- Repairing data values — only config correction
- Section-aware repair for complex files

## Acceptance criteria

Stage 5 is complete when:

- `mvn test` passes (all existing Stage 1-4 tests + new Stage 5 tests)
- The repair loop is bounded (max retries configurable)
- Repair prompt includes: original sample, broken config, and errors
- StubLlmClient repair stops after 1 attempt (no improvement possible)
- When max retries exceeded, the failure is exposed clearly
- When repair succeeds, the corrected config is valid
- Deterministic tests exist for all repair paths
- CLI `--repair-max` and `--no-repair` flags work
