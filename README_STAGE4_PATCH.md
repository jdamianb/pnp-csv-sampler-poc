# README Stage 4 Patch

Add a Stage 4 section to `README.md` after the Stage 3 section.

## Stage 4 — Simple Open PnP Parser Dry-run

Stage 4 adds a small original parser for the public PoC.

The original plan was to integrate the real company parser, but that parser is proprietary and is not used in this repository.

The Stage 4 parser is intentionally simple. It validates whether a `PnpImportFormatConfig` can parse a PnP CSV/TSV-like file into normalized placement preview rows.

### CLI Usage

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse examples/simple-pnp.csv expected-configs/simple-pnp.config.json
```

### Pipeline

```text
parse <file> <config-json>
  ├── Read config JSON
  ├── Validate PnpImportFormatConfig
  ├── SimplePnpParser
  │     ├── apply ignored lines and data bounds
  │     ├── resolve HEADER_NAME / COLUMN_INDEX mappings
  │     ├── parse X/Y/angle numbers
  │     ├── apply side value mappings
  │     └── collect row-level errors
  └── Output: PnpParseDryRunReport JSON
```

### Supported cases

- comma-delimited files
- semicolon-delimited files
- tab-delimited files
- simple no-header files using column indexes
- decimal separator `.` and `,`
- simple unit suffixes such as `mm`
- side mappings such as `T → Top`, `B → Bottom`

### Limitations

This is not a production parser.

It does not use proprietary company parser code.

It is only intended to validate LLM-proposed configs in the public PoC.
