# ADR 0004: Simple Open PnP Parser for Public PoC

## Status

Accepted (Stage 4 — implemented)

## Context

The original Stage 4 plan expected integration with an existing company parser.

That parser is proprietary and not available in this public repository.

The PoC still needs a deterministic way to check whether an LLM-proposed `PnpImportFormatConfig` can parse a file.

## Decision

Implement a small original parser in this repository.

The parser will be used only for PoC dry-run validation.

It will parse synthetic PnP CSV/TSV-like examples according to `PnpImportFormatConfig`.

It will produce a `PnpParseDryRunReport`.

## Consequences

Positive:

- The PoC can continue without proprietary code.
- The repository remains safe to publish.
- The LLM detector can be validated deterministically.
- Stage 5 repair loop can use parser errors as feedback.
- The parser is easy to understand and test.

Negative:

- The parser will not match every production behavior.
- The parser may not support complex real machine formats.
- The parser is not a replacement for a production parser.
- Some difficult examples may require future config extensions.

## Alternatives considered

### Use the company parser

Rejected.

It is proprietary and not available in this public PoC.

### Ask the LLM to parse rows directly

Rejected.

That would make parsing non-deterministic and harder to validate.

### Implement a production-grade parser

Rejected for the PoC.

The goal is dry-run validation, not a complete production import engine.

## Follow-up decisions

Future ADRs may cover:

- repair loop using parser errors
- section-aware parsing
- storing accepted configs
- evaluation reports
