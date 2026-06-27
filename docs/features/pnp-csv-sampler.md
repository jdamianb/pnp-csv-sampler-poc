# Feature: PnP CSV Sampler

## Summary

A deterministic, zero-dependency (beyond Jackson) CLI tool that reads Pick-and-Place CSV files as raw text and produces a JSON sample.

## Status

✅ Implemented, tested, reviewed, and approved.

## Input

- File path (required)
- `--first N` (optional, default 80)
- `--last M` (optional, default 20)

## Output

JSON to stdout with three fields:

- `totalLines` — total number of lines in the file
- `firstLines` — array of `{index, text}` objects from the start of the file
- `lastLines` — array of `{index, text}` objects from the end of the file (excluding any already in firstLines)

## Key Behavior

| Rule | Description |
|---|---|
| Raw text preservation | Line content is never trimmed, split, or interpreted |
| Zero-based indexing | First line is index 0 |
| No duplicates | Lines already captured in firstLines are excluded from lastLines |
| Streaming | Only `firstCount + lastCount` lines are held in memory |
| Error handling | Missing file, invalid args, non-positive counts → exit code 1 + stderr message |

## Files

| File | Lines | Description |
|---|---|---|
| `src/main/java/com/example/pnp/Main.java` | 129 | CLI entry point and argument parsing |
| `src/main/java/com/example/pnp/Sampler.java` | 96 | Core sampling logic with ring buffer |
| `src/main/java/com/example/pnp/Line.java` | 9 | Record: `int index, String text` |
| `src/main/java/com/example/pnp/SampleResult.java` | 17 | Record: `long totalLines, List<Line> firstLines, List<Line> lastLines` |
| `src/test/java/com/example/pnp/SamplerTest.java` | 314 | 19 unit tests |
| `src/test/java/com/example/pnp/MainTest.java` | 161 | 12 CLI tests |

## Gherkin Coverage

All 20 acceptance scenarios from the specification (`docs/specs/pnp-csv-sampler-spec.md`) are covered by automated tests.
