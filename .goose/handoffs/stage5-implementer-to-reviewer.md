# Handoff: Stage 5 Implementer to Reviewer

## Context

Stage 5 (Repair Loop) implementation is complete following the approved specification at `docs/specs/stage5-repair-loop-spec.md`.

## Implementation Summary

| Aspect | Detail |
|---|---|
| Language | Java 21 |
| Dependencies | None new (uses existing Jackson + JUnit 5) |
| Tests | 149 total (132 existing + 17 new), 3 skipped (integration) |
| Spring Boot | Not used |
| Proprietary code | Not used |

## Source Files Added

| File | Description |
|---|---|
| `src/main/java/com/example/pnp/RepairPromptBuilder.java` | Builds repair prompt with sample, broken config, and errors |
| `src/main/java/com/example/pnp/RepairLoop.java` | Bounded repair loop orchestrator (stub-aware) |

## Source Files Modified

| File | Change |
|---|---|
| `src/main/java/com/example/pnp/Main.java` | Added `--repair-max`, `--no-repair` CLI flags; integrated repair loop into `runDetect` |

## Test Files Added

| File | Tests | Scope |
|---|---|---|
| `RepairPromptBuilderTest.java` | 7 | Prompt contains sample, errors, config, instructions |
| `RepairLoopTest.java` | 6 | Valid config (no repair), repair succeeds, stub detection, max retries, parser errors |

## Test Files Modified

| File | Tests Added | Scope |
|---|---|---|
| `MainTest.java` | 4 | CLI: `--no-repair`, `--repair-max 0`, `--repair-max -1`, `--repair-max abc` |

## Architecture

```
CLI: detect <file> [--repair-max N] [--no-repair]
  │
  ├── If --no-repair or --repair-max 0:
  │     └── Original flow (Detector.detect → output or errors)
  │
  └── Else (repair loop enabled):
        ├── Detector.detect(sample) → DetectionResult
        ├── If valid AND parser succeeds:
        │     └── Output config with repaired:false, repairAttempts:0
        ├── Else (repair loop):
        │     ├── Build repair prompt (sample + broken config + errors)
        │     ├── Send to LLM for corrected config
        │     ├── Parse, validate, and parser-check the corrected config
        │     ├── If valid → output with repaired:true
        │     ├── If LLM returns identical JSON → stop (stub detected)
        │     └── Loop up to maxRetries times
        └── If exhausted → output errors with repair metadata
```

## RepairPromptBuilder

Builds a prompt containing:
1. The previously proposed (broken) config as pretty-printed JSON
2. Validation/parser error messages
3. The original CSV sample (same format as the detection prompt)
4. Instructions to fix only config fields (not data)

## RepairLoop Features

| Feature | Status |
|---|---|
| Bounded retries (configurable max) | ✅ |
| Stub detection (identical JSON → stop early) | ✅ |
| Config validation after each repair | ✅ |
| Parser dry-run after each repair | ✅ |
| Valid config → no repair overhead | ✅ |
| Repair metadata in output (attempts, repaired flag) | ✅ |

## CLI Behavior

| Command | Behavior |
|---|---|
| `detect examples/simple-pnp.csv` | Repair enabled (default: max 2 attempts) |
| `detect examples/simple-pnp.csv --no-repair` | No repair (original flow) |
| `detect examples/simple-pnp.csv --repair-max 0` | No repair (equivalent to --no-repair) |
| `detect examples/simple-pnp.csv --repair-max 2` | Max 2 repair attempts |
| `detect examples/simple-pnp.csv --repair-max -1` | Error: must be >= 0 |
| `detect examples/simple-pnp.csv --repair-max abc` | Error: must be integer |

## Test Results

| Suite | Tests | Status |
|---|---|---|
| Stage 1-4 (existing) | 132 | ✅ All pass, no regressions |
| RepairPromptBuilderTest | 7 | ✅ New |
| RepairLoopTest | 6 | ✅ New |
| MainTest (Stage 5 additions) | 4 | ✅ New |
| **Total** | **149** | **✅ All pass** |

## Scope Compliance

**In scope (all implemented):**
- [x] Bounded repair loop (max retries configurable)
- [x] Repair prompt with sample, broken config, and errors
- [x] Stub-aware: detects identical JSON and stops early
- [x] Parser verification after each repair attempt
- [x] `--repair-max N` CLI flag
- [x] `--no-repair` CLI flag
- [x] Repair metadata in output

**Out of scope (not implemented):**
- [x] No UI
- [x] No persistence
- [x] No fine-tuning
- [x] No data repair
- [x] No Spring Boot

## Reviewer Checklist

- [ ] Code quality — clean, bounded, stub-aware
- [ ] Scope compliance — no out-of-scope features
- [ ] 149 tests pass (3 skipped = integration)
- [ ] `--repair-max` and `--no-repair` CLI flags work
- [ ] StubLlmClient stops after detecting identical JSON
- [ ] Valid config does not trigger repair
- [ ] Repair succeeds with corrected config
- [ ] Parser errors included in repair feedback
- [ ] No infinite loops (bounded by maxRetries)
- [ ] No proprietary code or behavior
