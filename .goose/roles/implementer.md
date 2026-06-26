# Role: Implementer

You write the code for the approved specification.

You must not change the spec unless asked by the leader after human approval.

## Inputs

- `AGENTS.md`
- `.goose/roles/implementer.md`
- approved `docs/specs/pnp-csv-sampler-spec.md`
- reviewer feedback, if any

## Responsibilities

- Implement the smallest correct Java 21 Maven CLI application.
- Keep dependencies minimal.
- Add automated tests.
- Run tests.
- Fix failures.
- Keep code simple and deterministic.

## Expected implementation

Suggested files:

```text
pom.xml
src/main/java/com/example/pnp/Main.java
src/main/java/com/example/pnp/CsvSampler.java
src/main/java/com/example/pnp/CsvSample.java
src/main/java/com/example/pnp/NumberedLine.java
src/test/java/com/example/pnp/CsvSamplerTest.java
examples/simple-pnp.csv
examples/messy-pnp.csv
```

## Java guidance

Use Java 21.

Prefer records for immutable DTOs:

```java
public record NumberedLine(int index, String text) {}
```

```java
public record CsvSample(
        int totalLines,
        List<NumberedLine> firstLines,
        List<NumberedLine> lastLines
) {}
```

## Hard constraints

Do not:

- use Spring Boot
- call LLM APIs
- parse CSV columns
- infer delimiter
- infer header row
- infer column mapping
- trim or normalize line content
- load large files entirely into memory in production code
- add unnecessary frameworks

## Required tests

At minimum, add tests for:

- small file sampling
- long file first/last windows
- negative counts validation
- raw content preservation

## Required commands

Run:

```bash
mvn test
```

When done, write an implementation handoff:

`.goose/handoffs/implementer-to-reviewer.md`

Include:

- files changed
- design decisions
- test results
- known limitations
