# AGENTS.md

This project uses a role-based Goose agent harness.

All agents must follow this document before editing files.

## Project goal

Build a Java 21 PoC for a Pick-and-Place CSV sampler.

The sampler reads a CSV-like PnP export as raw text and produces a JSON sample containing:

- `totalLines`
- `firstLines`
- `lastLines`

Each sampled line must preserve:

- zero-based line index
- original raw line text exactly

## Core rule

The sampler is intentionally dumb and deterministic.

It must not:

- parse CSV fields
- infer delimiter
- infer header row
- infer column mappings
- infer units
- normalize decimals
- trim line content
- call any LLM API
- use Spring Boot

## Documentation reference

Feature documentation is stored under `docs/` with the feature name as the file name.

- `docs/feature-requests/pnp-csv-sampler-poc.md` — original feature request
- `docs/specs/pnp-csv-sampler-spec.md` — EARS requirements and Gherkin scenarios
- `docs/features/pnp-csv-sampler.md` — implemented feature documentation
- `docs/adr/pnp-csv-sampler.md` — Architecture Decision Records
- `docs/testing/pnp-csv-sampler.md` — test strategy and coverage

Agents should read the relevant docs before working on a feature.

## Roles

Role definitions are stored under `.goose/roles/`.

- Leader: `.goose/roles/leader.md`
- Spec Writer: `.goose/roles/spec-writer.md`
- Implementer: `.goose/roles/implementer.md`
- Reviewer: `.goose/roles/reviewer.md`
- Doc Writer: `.goose/roles/doc-writer.md`

## Workflow

The workflow is defined in:

`.goose/workflows/poc-feature-workflow.md`

No role may skip its phase.

## Phase 1: Leader intake

The leader reads the feature request and assigns the spec writer.

Input:

`docs/feature-requests/pnp-csv-sampler-poc.md`

Output:

`.goose/handoffs/001-leader-to-spec-writer.md`

## Phase 2: Spec writer

The spec writer creates:

- EARS requirements
- Gherkin acceptance scenarios
- explicit out-of-scope list
- implementation-neutral behavior rules

Output:

`docs/specs/pnp-csv-sampler-spec.md`

Then the leader must stop and request human approval.

Implementation cannot begin before human approval.

## Phase 3: Implementer

The implementer reads only approved specs and builds the smallest correct implementation.

Expected implementation:

- Java 21
- Maven
- Jackson
- JUnit 5
- optional Cucumber only if approved by the human
- no Spring Boot

Output:

- source code
- tests
- implementation handoff

## Phase 4: Reviewer

The reviewer checks:

- code quality
- scope compliance
- tests
- Gherkin scenario coverage
- CLI behavior
- no overengineering

The reviewer and implementer may loop until tests pass and quality is acceptable.

Output:

`.goose/reviews/pnp-csv-sampler-review.md`

Then the leader must request human implementation approval.

## Phase 5: Doc writer

The doc writer updates:

- `docs/`
- `README.md`
- `AGENTS.md`, only if the harness or workflow changed

Output:

`.goose/handoffs/doc-writer-completion.md`

## Required commands

The implementer and reviewer should run:

```bash
mvn test
```

If packaging is configured:

```bash
mvn clean package
```

If the CLI is implemented:

```bash
java -cp target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar com.example.pnp.Main examples/simple-pnp.csv
```

## Stop conditions

The leader must stop and ask for human review when:

1. Specs are drafted.
2. Reviewer accepts implementation.
3. Documentation is updated.

## Final PoC completion criteria

The feature is complete only when:

- specs are approved
- tests pass
- reviewer accepts implementation
- human approves implementation
- docs are updated
- final summary is written
