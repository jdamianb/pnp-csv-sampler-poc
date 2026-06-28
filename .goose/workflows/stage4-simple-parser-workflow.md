# Stage 4 Workflow: Simple Open PnP Parser Dry-run

This workflow replaces the earlier Stage 4 idea of integrating a proprietary company parser.

The Stage 4 implementation must create a simple, original, open parser for the public PoC.

## Phase 0: Leader intake

Leader reads:

- `AGENTS.md`
- `.goose/roles/leader.md`
- `docs/feature-requests/stage4-simple-pnp-parser-dry-run.md`
- current Stage 1, Stage 2, and Stage 3 docs
- current source tree

Leader confirms:

- Stage 1 sampler exists
- Stage 2 format config exists
- Stage 3 LLM adapter does not break deterministic tests
- no proprietary parser is available or allowed

Leader creates:

```text
.goose/handoffs/stage4-leader-to-spec-writer.md
```

## Phase 1: Specification

Spec writer creates:

```text
docs/specs/stage4-simple-pnp-parser-dry-run-spec.md
```

The spec must include:

- EARS requirements
- Gherkin scenarios
- supported delimiters
- row mapping behavior
- numeric parsing behavior
- dry-run report model
- error handling
- IP/proprietary-data boundary
- explicit out-of-scope list
- human review checklist

Leader stops for human approval.

## Phase 2: Implementation

After human spec approval, implementer adds the smallest correct implementation.

Suggested outputs:

```text
src/main/java/com/example/pnp/SimplePnpParser.java
src/main/java/com/example/pnp/PnpPlacement.java
src/main/java/com/example/pnp/PnpParseDryRunReport.java
src/main/java/com/example/pnp/DelimitedLineTokenizer.java
src/main/java/com/example/pnp/NumericValueParser.java
src/test/java/com/example/pnp/SimplePnpParserTest.java
src/test/java/com/example/pnp/DelimitedLineTokenizerTest.java
src/test/java/com/example/pnp/NumericValueParserTest.java
```

The implementer may adjust class names, but must keep the design simple.

Required command:

```bash
mvn test
```

The implementer creates:

```text
.goose/handoffs/stage4-implementer-to-reviewer.md
```

## Phase 3: Review loop

Reviewer checks:

- no proprietary code or data
- no company parser integration
- deterministic tests
- config validator reused
- parser dry-run report is useful
- CLI command works
- comma, semicolon, tab, and no-header cases are covered
- row-level errors are clear
- no repair loop yet
- no UI, database, RAG, or fine-tuning

If changes are required:

```text
Reviewer -> Implementer -> Reviewer
```

Reviewer creates:

```text
.goose/reviews/stage4-simple-pnp-parser-review.md
```

Leader stops for human implementation approval.

## Phase 4: Documentation

After human implementation approval, doc writer updates:

```text
docs/features/stage4-simple-pnp-parser-dry-run.md
docs/testing/stage4-simple-pnp-parser-dry-run.md
README.md
AGENTS.md
```

Doc writer creates:

```text
.goose/handoffs/stage4-doc-writer-completion.md
```

Leader stops for final human review.

## Phase 5: Next stage planning

Do not implement the repair loop automatically.

After Stage 4 is accepted, the next likely stage is:

```text
Stage 5: bounded repair loop using parser dry-run errors
```
