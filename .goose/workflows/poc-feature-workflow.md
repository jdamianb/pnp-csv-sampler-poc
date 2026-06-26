# PoC Feature Workflow

This workflow is optimized for small, controlled proof-of-concept work.

## Phase 0: Intake

Leader reads:

- `AGENTS.md`
- feature request
- role definitions

Leader creates:

`.goose/handoffs/001-leader-to-spec-writer.md`

## Phase 1: Specification

Spec writer creates:

`docs/specs/pnp-csv-sampler-spec.md`

Spec must include:

- EARS requirements
- Gherkin scenarios
- clear out-of-scope list

Leader stops for human approval.

## Phase 2: Implementation

After human approval, implementer writes the smallest correct implementation.

Implementer must run:

```bash
mvn test
```

Implementer creates:

`.goose/handoffs/implementer-to-reviewer.md`

## Phase 3: Review loop

Reviewer checks code, tests, and scope.

If changes are required:

```text
Reviewer -> Implementer -> Reviewer
```

The loop continues until:

- tests pass
- reviewer accepts
- scope is respected

Reviewer creates:

`.goose/reviews/pnp-csv-sampler-review.md`

Leader stops for human implementation approval.

## Phase 4: Documentation

After human implementation approval, doc writer updates docs.

Doc writer creates:

`.goose/handoffs/doc-writer-completion.md`

Leader stops for final human review.

## Phase 5: Completion

Leader writes final summary.

The feature is complete only when:

- spec approved
- implementation approved
- docs approved
