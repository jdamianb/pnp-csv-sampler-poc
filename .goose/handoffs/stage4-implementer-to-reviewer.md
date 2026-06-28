# Handoff: Stage 4 Implementer to Reviewer

## Context

Stage 4 (Simple Open PnP Parser Dry-run) implementation is complete following the approved specification at `docs/specs/stage4-simple-pnp-parser-dry-run-spec.md`.

The implementation adds a small, original, deterministic parser that validates whether a proposed `PnpImportFormatConfig` can actually parse the file.

## Implementation Summary

| Aspect | Detail |
|---|---|
| Language | Java 21 |
| Build tool | Maven 3.8.7 |
| Dependencies | None new (uses existing Jackson + JUnit 5) |
| Tests | 132 total (109 existing + 23 new), 3 skipped (integration) |
| Spring Boot | **Not used** |
| Proprietary code | **Not used** — all original, synthetic tests only |

## Source Files Added

| File | Description |
|---|---|
| `src/main/java/com/example/pnp/PnpPlacement.java` | Record: reference, partNumber, jedec, x, y, angle, side |
| `src/main/java/com/example/pnp/PnpParseDryRunReport.java` | Record: success, counts, errors, warnings, columns, placements |
| `src/main/java/com/example/pnp/SimplePnpParser.java` | Deterministic parser: 436 lines, all original |

## Source Files Modified

| File | Change |
|---|---|
| `src/main/java/com/example/pnp/Main.java` | Added `parse <file> <config-json>` CLI command |

## Test Files Added

| File | Tests | Scope |
|---|---|---|
| `SimplePnpParserTest.java` | 23 | Comma, semicolon, tab, whitespace, COLUMN_INDEX, error paths, bounds, empty file, unit stripping, side mapping, num parsing, split helpers |

## Architecture

```
CLI: parse <file> <config-json>
  │
  ├── Read config JSON → PnpImportFormatConfig
  ├── PnpImportFormatConfigValidator → validate config
  ├── SimplePnpParser.parse(reader, config)
  │     ├── Read all lines
  │     ├── Resolve HEADER_NAME / COLUMN_INDEX column positions
  │     ├── Apply linesToIgnore, dataStartRowIndex, dataEndRowIndex
  │     ├── Split rows on delimiter (",", ";", "\t", WHITESPACE)
  │     ├── Parse X/Y/angle as doubles with decimal separator handling
  │     ├── Strip unit suffixes (mm, deg, mil, inch, etc.)
  │     ├── Apply side value mappings
  │     └── Collect row-level errors
  │
  └── Output: PnpParseDryRunReport JSON
```

## Supported Features

| Feature | Status |
|---|---|
| Comma delimiter (`,`) | ✅ |
| Semicolon delimiter (`;`) | ✅ |
| Tab delimiter (`\t` or `\\t`) | ✅ |
| Whitespace delimiter (`WHITESPACE`, variable-width) | ✅ |
| HEADER_NAME column mapping | ✅ |
| COLUMN_INDEX column mapping | ✅ |
| Header `#` prefix handling (KiCad .pos) | ✅ |
| Decimal separator `.` | ✅ |
| Decimal separator `,` (European) | ✅ |
| Thousands separator removal (comma-decimal) | ✅ |
| Unit suffix stripping (mm, deg, mil, inch, cm) | ✅ |
| Side value mappings (case-insensitive) | ✅ |
| linesToIgnore | ✅ |
| dataStartRowIndex / dataEndRowIndex | ✅ |
| Row-level error reporting | ✅ |
| Sample placements (max 10) | ✅ |
| Available columns from header | ✅ |
| Basic CSV quote handling | ✅ |

## CLI Commands

```bash
# Parse using an expected config
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse examples-extended/03_jlcpcb_cpl_minimal.csv expected-configs/03_jlcpcb_cpl_minimal.config.json

# Parse whitespace-delimited KiCad file
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse examples-extended/01_kicad_native_ascii_mm.pos expected-configs/01_kicad_native_ascii_mm.config.json

# Parse semicolon-delimited European file
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse examples-extended/08_machine_semicolon_decimal_comma.csv expected-configs/08_machine_semicolon_decimal_comma.config.json
```

## CLI Verification Results

| File | Delimiter | Result |
|---|---|---|
| `03_jlcpcb_cpl_minimal.csv` | `,` | ✅ 4 rows |
| `01_kicad_native_ascii_mm.pos` | WHITESPACE | ✅ 4 rows |
| `08_machine_semicolon_decimal_comma.csv` | `;` (`,` decimal) | ✅ 4 rows |
| `09_machine_tab_separated_with_metadata.tsv` | `\t` | ✅ 4 rows |
| `10_machine_no_header_column_index.csv` | `,` (COLUMN_INDEX) | ✅ 4 rows |

## Scope Compliance

**In scope (all implemented):**
- [x] `PnpPlacement` normalized placement DTO
- [x] `PnpParseDryRunReport` dry-run report DTO
- [x] `SimplePnpParser` deterministic parser
- [x] `parse <file> <config-json>` CLI command
- [x] Comma, semicolon, tab, WHITESPACE delimiters
- [x] HEADER_NAME and COLUMN_INDEX mappings
- [x] `.` and `,` decimal separators with unit suffix stripping
- [x] Side value mappings
- [x] Row-level errors
- [x] Config validation before parsing

**Out of scope (not implemented):**
- [x] No proprietary parser code or behavior
- [x] No production-grade parser
- [x] No repair loop (Stage 5)
- [x] No UI
- [x] No database
- [x] No RAG or fine-tuning
- [x] No LLM-based row parsing
- [x] No Spring Boot

## Reviewer Checklist

- [ ] Code quality — clean, original implementation
- [ ] Scope compliance — no out-of-scope features introduced
- [ ] All 132 tests pass (3 skipped = integration)
- [ ] `parse` CLI command works with all 4 delimiter types
- [ ] Error handling covers config not found, invalid JSON, validation failure
- [ ] Row-level errors for non-numeric X/Y/angle
- [ ] No proprietary code or behavior
- [ ] All synthetic test data
- [ ] No Spring Boot
- [ ] No new dependencies
