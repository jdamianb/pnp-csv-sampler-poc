# Handoff: Leader to Spec Writer

## Context

This project builds a **Java 21 PoC for a Pick-and-Place CSV sampler**. The sampler reads a CSV-like PnP export as raw text and produces a JSON sample containing `totalLines`, `firstLines`, and `lastLines`. Each sampled line must preserve its zero-based line index and original raw text exactly.

The sampler is intentionally **dumb and deterministic** — it must not parse CSV fields, infer delimiters, headers, column mappings, or units. It must not call any LLM API or use Spring Boot.

The feature request has been reviewed and accepted as the basis for this specification.

## Inputs

- `AGENTS.md` — project harness, roles, and workflow
- `.goose/roles/spec-writer.md` — spec writer role definition
- `docs/feature-requests/pnp-csv-sampler-poc.md` — feature request with examples and expected JSON shape

## Expected outputs

Create:

`docs/specs/pnp-csv-sampler-spec.md`

The spec must contain:

1. **Purpose** — what the PnP CSV sampler does
2. **Scope** — what is included
3. **Out of scope** — explicit exclusion list
4. **Assumptions** — design assumptions
5. **EARS requirements** — structured requirements using EARS patterns
6. **Gherkin acceptance scenarios** — behavioral scenarios
7. **Non-functional requirements** — performance, encoding, streaming
8. **Testability notes** — how to verify correctness
9. **Human review checklist** — what reviewers should verify

## Constraints

The spec must preserve PoC scope. Do not specify:

- Spring Boot
- REST endpoints
- LLM integration
- delimiter detection
- header detection
- actual CSV field parsing
- database storage
- UI
- fine-tuning
- any form of content normalization or trimming

The sampler must remain **dumb and deterministic** — it reads raw lines and samples them by index only.

## Stop condition

After writing `docs/specs/pnp-csv-sampler-spec.md`, return control to the leader.

The leader will stop and request **human approval** before implementation can begin.

Do not hand off to the implementer.
