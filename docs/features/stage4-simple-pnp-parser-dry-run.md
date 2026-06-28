# Feature: Stage 4 — Simple Open PnP Parser Dry-run

## Summary

A deterministic, original CSV/TSV parser that consumes a `PnpImportFormatConfig` and an input PnP file, parses the rows according to the config, and produces a `PnpParseDryRunReport`.

This serves as the dry-run authority for the PoC — it validates whether a proposed import configuration can actually parse the file.

## Status

✅ Implemented, tested, reviewed, and approved (Stage 4).

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

## Source Files

| File | Lines | Description |
|---|---|---|
| `PnpPlacement.java` | 22 | Record: reference, partNumber, jedec, x, y, angle, side |
| `PnpParseDryRunReport.java` | 29 | Record: success, counts, errors, warnings, columns, placements |
| `SimplePnpParser.java` | 436 | Deterministic parser (all original, no proprietary code) |
| `Main.java` (modified) | — | Added `parse <file> <config-json>` CLI command |

## Supported Delimiters

| Delimiter | Config Value | Example File |
|---|---|---|
| Comma | `,` | JLCPCB, Altium, EasyEDA |
| Semicolon | `;` | European format with decimal comma |
| Tab | `\t` or `\\t` | Machine TSV with metadata |
| Whitespace (variable-width) | `WHITESPACE` | KiCad `.pos` format |

## Supported Column Mappings

| Source | Description | Example |
|---|---|---|
| `HEADER_NAME` | Column identified by its header text | `"value": "Ref"` |
| `COLUMN_INDEX` | Column identified by zero-based index | `"value": "3"` |

## PnpPlacement Model

```json
{
  "reference": "C1",
  "partNumber": "100nF",
  "jedec": "C_0603",
  "x": 95.0518,
  "y": 22.6822,
  "angle": 270.0,
  "side": "Top"
}
```

## PnpParseDryRunReport Model

```json
{
  "success": true,
  "totalCandidateRows": 4,
  "parsedRows": 4,
  "rejectedRows": 0,
  "errors": [],
  "warnings": [],
  "availableColumns": ["Designator", "Comment", "Footprint", "Mid X", "Mid Y", "Rotation", "Layer"],
  "samplePlacements": [
    { "reference": "C1", "partNumber": "100nF", "jedec": "C_0603", "x": 95.0518, "y": 22.6822, "angle": 270.0, "side": "Top" }
  ]
}
```

## CLI Usage

```bash
# Parse a comma-delimited file
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse examples-extended/03_jlcpcb_cpl_minimal.csv expected-configs/03_jlcpcb_cpl_minimal.config.json

# Parse a whitespace-delimited KiCad file
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse examples-extended/01_kicad_native_ascii_mm.pos expected-configs/01_kicad_native_ascii_mm.config.json

# Parse a semicolon-delimited European file with decimal comma
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse examples-extended/08_machine_semicolon_decimal_comma.csv expected-configs/08_machine_semicolon_decimal_comma.config.json

# Parse a tab-delimited file
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse examples-extended/09_machine_tab_separated_with_metadata.tsv expected-configs/09_machine_tab_separated_with_metadata.config.json

# Parse with COLUMN_INDEX mappings (no header)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse examples-extended/10_machine_no_header_column_index.csv expected-configs/10_machine_no_header_column_index.config.json
```

## Supported Features

| Feature | Status |
|---|---|
| Comma delimiter | ✅ |
| Semicolon delimiter | ✅ |
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

## Design Decisions

- **Original implementation**: The parser was written from scratch — no proprietary company parser code, behavior, or data
- **PoC scope**: Simple and readable, not production-grade
- **Reader-based**: The parser accepts `Reader` for testability (tests use `StringReader`)
- **No LLM during parsing**: Every row is parsed deterministically — no LLM calls
- **Row-level errors**: Each failing row gets a specific error message with the original line number
- **Original line indexes**: All indexes (headerRowIndex, dataStartRowIndex, etc.) use original zero-based line numbers from the input file, avoiding index shift issues
- **Config is always validated first**: The existing `PnpImportFormatConfigValidator` is run before parsing begins
