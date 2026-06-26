# Handoff: Implementer to Reviewer

## Context

Phase 3 (Implementation) of the PnP CSV Sampler PoC is complete. The implementation follows the approved specification at `docs/specs/pnp-csv-sampler-spec.md`.

## Implementation Summary

| Aspect | Detail |
|---|---|
| Language | Java 21 |
| Build tool | Maven 3.8.7 |
| Dependencies | Jackson 2.17.2, JUnit 5.10.3 |
| Package | `com.example.pnp` |
| JAR | `target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar` (shaded uber-jar) |
| Tests | 31 tests (19 SamplerTest + 12 MainTest), all passing |
| Spring Boot | **Not used** |

## Source Files

| File | Description |
|---|---|
| `src/main/java/com/example/pnp/Line.java` | Record with `index` (int) and `text` (String) |
| `src/main/java/com/example/pnp/SampleResult.java` | Record with `totalLines`, `firstLines`, `lastLines` |
| `src/main/java/com/example/pnp/Sampler.java` | Core logic: accepts `Reader`, streams file, ring buffer for tail |
| `src/main/java/com/example/pnp/Main.java` | CLI: parses args, opens file, delegates to Sampler, prints JSON |
| `src/test/java/com/example/pnp/SamplerTest.java` | 19 tests covering all Gherkin scenarios |
| `src/test/java/com/example/pnp/MainTest.java` | 12 tests covering CLI error handling |

## Architecture

- **Sampler** accepts a `java.io.Reader` → fully testable without filesystem
- **Main** resolves file path, opens `InputStreamReader`, delegates to Sampler
- Ring buffer (`ArrayDeque` with max size `lastCount`) for streaming tail
- Jackson `ObjectMapper` for JSON output with `INDENT_OUTPUT`
- CLI: `java ... Main <file> [--first N] [--last M]`

## Key Design Decisions

1. **R10 (no duplicates)**: When `totalLines <= firstCount + lastCount`, lastLines filters out entries already in firstLines (by index >= firstCount).
2. **Streaming**: Only `firstCount + lastCount` lines held in memory at any time.
3. **Error handling**: File-not-found, invalid args, non-positive counts all return exit code 1 with error message to stderr.
4. **Raw text preservation**: `BufferedReader.readLine()` strips line terminators but preserves all other content exactly.

## Verification

```bash
mvn test                    # 31 tests, all pass
mvn clean package           # builds shaded uber-jar
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/simple-pnp.csv  # 8 lines, correct JSON
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/messy-pnp.csv   # 13 lines, correct JSON
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar nonexistent.csv          # exit 1, error to stderr
```

## Scope Compliance

**In scope (all implemented):**
- [x] Read raw text, preserve line content exactly
- [x] Zero-based line indexing
- [x] totalLines, firstLines (default 80), lastLines (default 20)
- [x] Custom --first and --last flags
- [x] Streaming (no loading entire file)
- [x] JSON output to stdout
- [x] UTF-8 encoding
- [x] Error handling for missing file, invalid args

**Out of scope (not implemented):**
- [x] No CSV field parsing
- [x] No delimiter inference
- [x] No header detection
- [x] No column mapping
- [x] No unit normalization
- [x] No LLM API calls
- [x] No Spring Boot
- [x] No REST API
- [x] No database
- [x] No UI

## Expected Outputs

- Source code deployed to `src/`
- Tests deployed to `src/test/`
- Example files at `examples/`
- Build artefact at `target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar`

## Reviewer Checklist

- [ ] Code quality — clean, well-structured, follows Java 21 idioms
- [ ] Scope compliance — no out-of-scope features introduced
- [ ] All 31 tests pass
- [ ] Gherkin scenario coverage verified
- [ ] CLI produces valid JSON
- [ ] Raw line content is never modified
- [ ] Streaming (no full-file array)
- [ ] No overengineering

## Stop Condition

Reviewer accepts or requests changes. If changes needed, loop back to implementer.
