# Handoff: Stage 4 Leader to Spec Writer

## Context

Stages 1 (CSV Sampler), 2 (Format Detection contract), and 3 (Ollama Real LLM Adapter) are complete.

Stage 4 has been redefined. Instead of integrating with a proprietary company parser (which is not available in this public repository), Stage 4 must implement a **simple, original, open PoC parser** that can validate whether a proposed `PnpImportFormatConfig` is usable.

## Inputs

- `AGENTS.md` — updated Stage 4 rules: parser is simple, open, original
- `docs/feature-requests/stage4-simple-pnp-parser-dry-run.md` — detailed feature request
- `docs/adr/0004-simple-open-pnp-parser-for-poc.md` — ADR (Proposed status)
- `src/main/java/com/example/pnp/PnpImportFormatConfig.java` — config model
- `src/main/java/com/example/pnp/ColumnMapping.java` — column mapping
- `src/main/java/com/example/pnp/ColumnSource.java` — HEADER_NAME / COLUMN_INDEX
- `src/main/java/com/example/pnp/PnpImportFormatConfigValidator.java` — validator (12 rules)
- `expected-configs/` — expected config JSONs for all 12 extended examples
- `examples-extended/` — 12 diverse PnP file examples

## Expected outputs

Create `docs/specs/stage4-simple-pnp-parser-dry-run-spec.md` containing:

1. **Purpose** — simple original parser to dry-run validate configs
2. **Scope** — parse config-driven rows, produce dry-run report, CLI `parse` command
3. **Out of scope** — no proprietary parser, no production-grade, no repair loop, no UI
4. **Assumptions** — PnpImportFormatConfig exists and is validated
5. **EARS requirements** — ubiquitous, event-driven, state-driven, unwanted behavior
6. **Gherkin acceptance scenarios** — covering all delimiter types, error cases, edge cases
7. **Non-functional requirements** — Java 21, Jackson, JUnit 5, no Spring Boot
8. **Testability notes** — Reader-based, no filesystem needed, synthetic examples only
9. **Human review checklist**

## Constraints

- **Must be original**: No proprietary parser code, behavior, examples, or data
- **Deterministic**: No LLM calls during parsing
- **Simple**: Not production-grade, not designed for every real machine format
- **Supports at least**: comma, semicolon, tab, WHITESPACE delimiters
- **Supports at least**: HEADER_NAME and COLUMN_INDEX mappings
- **Supports at least**: `.` and `,` decimal separators
- **Supports at least**: unit suffix stripping (e.g., `mm`)
- **Supports at least**: side value mappings
- **No Spring Boot**, no database, no UI, no RAG, no fine-tuning

## Suggested DTOs

```java
public record PnpPlacement(
    String reference,
    String partNumber,
    String jedec,
    double x,
    double y,
    double angle,
    String side
) {}

public record PnpParseDryRunReport(
    boolean success,
    int totalCandidateRows,
    int parsedRows,
    int rejectedRows,
    List<String> errors,
    List<String> warnings,
    List<String> availableColumns,
    List<PnpPlacement> samplePlacements
) {}
```

## Suggested CLI

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse <file> <config-json>
```

## Stop condition

After writing the spec, return control to the leader.

The leader will stop for human approval before implementation begins.
