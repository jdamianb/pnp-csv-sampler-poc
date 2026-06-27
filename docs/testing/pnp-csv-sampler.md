# Testing Documentation

## Test Suite

| Suite | File | Tests | Scope |
|---|---|---|---|
| SamplerTest | `src/test/java/com/example/pnp/SamplerTest.java` | 19 | Core sampling logic (Reader-based, no filesystem) |
| MainTest | `src/test/java/com/example/pnp/MainTest.java` | 12 | CLI argument parsing and error handling |
| **Total** | | **31** | |

## How to Run

```bash
# All tests
mvn test

# Single test class
mvn test -Dtest=SamplerTest

# Single test method
mvn test -Dtest=SamplerTest#emptyFile
```

## Gherkin Scenario Coverage

The 31 tests cover all 20 Gherkin acceptance scenarios from `docs/specs/pnp-csv-sampler-spec.md`:

| Scenario | Covered By |
|---|---|
| Default sampling (100 lines) | `defaultSamplingWithLargeFile` |
| Custom first/last counts | `customFirstAndLastCounts` |
| File smaller than requested sample | `fileSmallerThanRequestedSampleSize` |
| Preserve raw line content | `preservesRawLineContentExactly` |
| Zero-based indexing | `zeroBasedIndexing` |
| File not found | `MainTest.fileNotFound` |
| Missing file argument | `MainTest.missingFileArgument` |
| Invalid --first (negative/zero) | `MainTest.negativeFirstValue`, `MainTest.zeroFirstValue` |
| Invalid --last (negative/zero) | `MainTest.negativeLastValue`, `MainTest.zeroLastValue` |
| Valid JSON output | `jsonSerialization` |
| Empty file | `emptyFile` |
| Single line file | `singleLineFileWithOverlappingCounts` |
| Custom only-first | `customFirstOnly` |
| Custom only-last | `customLastOnly` |
| Streaming large file | `streamingDoesNotLoadEntireFile`, `veryLargeFileStreaming` |
| DOS line endings | `dosLineEndings` |
| No trailing newline | `noTrailingNewline` |
| Constructor rejects non-positive | 4 constructor tests |
| Overlapping samples (R10 dedup) | `partialOverlapBetweenFirstAndLast` |
| Unknown option | `MainTest.unknownOption` |

## Testing Principles

- **No filesystem needed**: `Sampler` accepts `java.io.Reader` — tests use `StringReader`
- **Edge cases covered**: empty file, single line, CRLF endings, no trailing newline, non-positive counts
- **Large file streaming**: verified with 100,000 and 1,000,000 line inputs
- **JSON round-trip**: sample output is serialized and deserialized back

## CLI Verification

Manual tests on example files:

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/simple-pnp.csv
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/messy-pnp.csv
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/simple-pnp.csv --first 4 --last 2
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar nonexistent.csv
```

## Known Limitations

- Line terminator characters (`\n`, `\r\n`) are stripped by `BufferedReader.readLine()` — only the content between terminators is preserved
- Maximum file size is limited only by available disk and the streaming capacity of `BufferedReader`
