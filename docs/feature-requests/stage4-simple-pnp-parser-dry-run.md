# Feature Request: Stage 4 Simple Open PnP Parser Dry-run

## Context

Stages 1, 2, and 3 exist or are in progress.

Stage 1 samples PnP CSV/TSV-like files.

Stage 2 proposes a `PnpImportFormatConfig`.

Stage 3 adds a real LLM adapter behind the existing detector pipeline.

The original Stage 4 plan assumed integration with an existing company CSV parser.

That parser is proprietary and is not available in this public PoC. It must not be used or copied.

## Goal

Implement a simple, original, open PoC parser that can validate whether a proposed `PnpImportFormatConfig` is usable.

The parser is not intended to be production-grade.

Its job is to perform a deterministic dry-run and produce a clear report.

## Target workflow

```text
PnP file + PnpImportFormatConfig
  ↓
simple original parser
  ↓
PnpParseDryRunReport
```

## Why this stage exists

The LLM can propose an import configuration, but the PoC needs a deterministic validator that checks whether the proposed configuration actually works on the file.

This parser becomes the dry-run authority for the public PoC.

## Important IP boundary

Do not use:

- proprietary company parser code
- proprietary company test files
- proprietary company internal format documentation
- proprietary parser behavior copied from memory or internal systems

The Stage 4 parser must be an original implementation based only on the public PoC config model and synthetic examples.

## Input

The parser receives:

1. A PnP CSV/TSV-like file.
2. A `PnpImportFormatConfig`.

The config may come from:

- a file under `expected-configs/`
- the stub detector
- Ollama/real LLM detector output
- a manually edited JSON config

## Output

The parser outputs a dry-run report, for example:

```json
{
  "success": true,
  "totalCandidateRows": 4,
  "parsedRows": 4,
  "rejectedRows": 0,
  "errors": [],
  "warnings": [],
  "availableColumns": ["Designator", "Mid X", "Mid Y", "Layer", "Rotation"],
  "samplePlacements": [
    {
      "reference": "C1",
      "partNumber": null,
      "jedec": null,
      "x": 95.0518,
      "y": 22.6822,
      "angle": 270.0,
      "side": "Top"
    }
  ]
}
```

## Required parser behavior

The parser must:

- read the input file as raw text
- use `PnpImportFormatConfig` to decide how to parse
- apply `linesToIgnore`
- honor `headerRowIndex`, when present
- honor `dataStartRowIndex`
- honor `dataEndRowIndex`, when present
- support mappings by `HEADER_NAME`
- support mappings by `COLUMN_INDEX`
- parse `x`, `y`, and `angle` as numbers
- support decimal separator `.` and `,`
- strip simple unit suffixes from coordinates, such as `mm`
- apply `valueMappings` for side/layer values
- produce row-level errors
- produce a clear dry-run report
- include a small sample of normalized placement rows

## Supported delimiters for Stage 4

The parser should support at least:

- comma: `,`
- semicolon: `;`
- tab: `	`
- simple whitespace delimiter, represented as `WHITESPACE`

If the current config uses another delimiter, the parser should reject it with a clear message.

## Required normalized placement model

Create a small DTO such as:

```java
public record PnpPlacement(
        String reference,
        String partNumber,
        String jedec,
        double x,
        double y,
        double angle,
        String side
) {
}
```

Fields may be adjusted if the existing domain names differ, but keep the model small.

## Required dry-run report model

Create a DTO such as:

```java
public record PnpParseDryRunReport(
        boolean success,
        int totalCandidateRows,
        int parsedRows,
        int rejectedRows,
        List<String> errors,
        List<String> warnings,
        List<String> availableColumns,
        List<PnpPlacement> samplePlacements
) {
}
```

The report must be serializable as JSON with Jackson.

## Suggested parser components

Suggested classes:

```text
src/main/java/com/example/pnp/
  SimplePnpParser.java
  PnpPlacement.java
  PnpParseDryRunReport.java
  ParsedTable.java
  DelimitedLineTokenizer.java
  NumericValueParser.java
  ConfigColumnResolver.java
```

Do not over-engineer the structure if fewer classes are enough.

## Suggested CLI behavior

Add a command such as:

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse examples/simple-pnp.csv expected-configs/simple-pnp.config.json
```

The command should:

1. Read the input file.
2. Read the config JSON.
3. Validate the config using the existing config validator.
4. Run the simple parser.
5. Print the dry-run report as pretty JSON.
6. Exit with non-zero status if the config is invalid or parsing fails.

If the current CLI uses another style, keep the existing style consistent.

## Optional combined command

Only if simple and approved, add:

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect-and-parse examples/simple-pnp.csv
```

This should:

1. Run detection.
2. Validate detected config.
3. Run simple parser dry-run.
4. Print both config and parse report.

This combined command is optional and should not block Stage 4.

## Out of scope

Do not implement:

- proprietary parser integration
- production-grade parser
- UI
- database
- RAG
- fine-tuning
- repair loop
- section-aware parser for complex sectioned files, unless separately approved
- advanced CAD/machine-specific behavior
- IPC/CAD-specific semantics beyond the config model

## Acceptance criteria

Stage 4 is complete when:

- `mvn test` passes.
- Existing sampler tests still pass.
- Existing detector tests still pass.
- Existing Ollama tests remain opt-in.
- `parse <file> <config-json>` outputs valid JSON.
- The parser accepts valid expected configs from `expected-configs/`.
- The parser rejects missing required mappings with clear errors.
- The parser rejects invalid numeric X/Y/angle values with row-level errors.
- The parser supports comma-delimited examples.
- The parser supports semicolon-delimited decimal-comma examples.
- The parser supports tab-delimited examples.
- The parser supports no-header column-index examples.
- The parser does not use proprietary company code or data.
- Documentation states that this is a simple original PoC parser, not the company parser.
