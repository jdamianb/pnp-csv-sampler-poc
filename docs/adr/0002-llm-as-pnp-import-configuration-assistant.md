# ADR 0002: LLM as PnP Import Configuration Assistant

## Status

Proposed

## Context

Users currently configure PnP imports manually by selecting ignored lines and mapping file columns to parser fields such as X, Y, angle, part number, JEDEC/package, side, and reference.

The project already has a CSV sampler that can produce numbered raw file samples.

The project also has or will integrate with an existing deterministic CSV parser.

## Decision

Use the LLM only as a PnP import configuration assistant.

The LLM proposes a `PnpImportFormatConfig`.

The existing parser remains responsible for parsing the file.

The LLM must not produce normalized component placement rows.

## Consequences

Positive:

- The tedious manual setup step can be reduced.
- Parser behavior remains deterministic.
- The LLM output can be validated before use.
- The system can later support local or cloud LLMs behind the same interface.
- The same config object can be reviewed or edited by a human.

Negative:

- The LLM may propose incorrect configs.
- A validator and later parser dry-run loop are required.
- The UI or CLI may still need human confirmation.

## Alternatives considered

### Let the LLM parse the complete file

Rejected.

This would make parsing non-deterministic and harder to validate.

### Fine-tune a model immediately

Rejected for the PoC.

Prompting plus validation should be tested first.

### Replace the existing parser

Rejected.

The existing parser remains the authority.

## Follow-up decisions

Future ADRs may cover:

- local LLM runtime choice
- parser dry-run repair loop
- storing reusable detected formats
- fine-tuning strategy after enough labeled examples exist
