# Goose Prompt: Start Stage 4 Simple Open Parser

Paste this into Goose from the repository root.

```text
Read AGENTS.md and follow the existing role workflow.

We completed Stage 1: CSV sampler.
We completed Stage 2: LLM-assisted format detection contract.
Stage 3 real LLM adapter is implemented or in testing.

We are changing Stage 4.

Important:
Do not integrate with the proprietary company parser.
Do not use proprietary company code.
Do not use proprietary company examples.
Do not copy proprietary parser behavior.

Stage 4 must implement a simple, original, open parser for this public PoC.

Use this feature request:
docs/feature-requests/stage4-simple-pnp-parser-dry-run.md

Goal:
Given a PnP file and a PnpImportFormatConfig, run a deterministic dry-run parser and output a PnpParseDryRunReport.

The parser should validate that the config can actually parse rows into a small normalized placement preview.

It should support:
- HEADER_NAME mappings
- COLUMN_INDEX mappings
- comma delimiter
- semicolon delimiter
- tab delimiter
- simple whitespace delimiter if reasonable
- decimal separator "." and ","
- simple unit suffix stripping such as "mm"
- side value mappings
- row-level errors

Out of scope:
- proprietary parser integration
- production-grade parser
- repair loop
- UI
- database
- RAG
- fine-tuning
- asking the LLM to parse rows

First:
The spec writer must create EARS requirements and Gherkin scenarios.
Stop for my approval before implementation.
```
