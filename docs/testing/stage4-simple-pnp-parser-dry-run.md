# Testing: Stage 4 — Simple Open PnP Parser Dry-run

## Test Suite

| Suite | File | Tests | Scope |
|---|---|---|---|
| SimplePnpParserTest | `SimplePnpParserTest.java` | 23 | Parser: all delimiter types, mappings, error paths, helpers |

## Test Coverage

### Delimiter Types

| Scenario | Test |
|---|---|
| Comma-delimited with HEADER_NAME | `commaDelimitedWithHeader` |
| Semicolon-delimited with decimal comma | `semicolonDelimitedWithDecimalComma` |
| Tab-delimited | `tabDelimited` |
| Whitespace-delimited with variable-width spaces | `whitespaceDelimited` |

### Column Mappings

| Scenario | Test |
|---|---|
| HEADER_NAME mapping | `commaDelimitedWithHeader` |
| COLUMN_INDEX mapping (no header) | `columnIndexMappings` |
| Header column not found | `headerColumnNotFound` |

### Error Handling

| Scenario | Test |
|---|---|
| Non-numeric X value | `nonNumericXProducesError` |
| Non-numeric angle value | `nonNumericAngleProducesError` |
| Empty file | `emptyFile` |
| Unrecognized delimiter (treated as literal) | `unrecognizedDelimiter` |
| Header column not found | `headerColumnNotFound` |

### Config Bounds

| Scenario | Test |
|---|---|
| linesToIgnore applied | `linesToIgnore` |
| dataStartRowIndex and dataEndRowIndex | `dataBounds` |

### Value Processing

| Scenario | Test |
|---|---|
| Unit suffix stripping (mm, deg) | `unitSuffixStripping` |
| Side value mapping (case-insensitive) | `sideValueMapping` |
| Report includes available columns | `reportIncludesAvailableColumns` |
| Sample placements limited to 10 | `samplePlacementsLimited` |

### Helper Methods

| Scenario | Test |
|---|---|
| parseDouble normal decimal | `parseDoubleNormal` |
| parseDouble comma decimal | `parseDoubleCommaDecimal` |
| parseDouble with unit suffix | `parseDoubleWithUnitSuffix` |
| parseDouble empty throws | `parseDoubleEmptyThrows` |
| splitLine comma | `splitLineComma` |
| splitLine whitespace | `splitLineWhitespace` |
| splitLine quoted fields | `splitLineQuoted` |

## Running Tests

```bash
# All tests (Stage 1-4)
mvn test

# Stage 4 parser tests only
mvn test -Dtest=SimplePnpParserTest

# Specific test method
mvn test -Dtest=SimplePnpParserTest#commaDelimitedWithHeader

# Build and test
mvn clean test
```

## Test Design

All parser tests are **deterministic and offline**:
- Use `StringReader` instead of files — no filesystem dependency
- Use inline config objects — no JSON files needed
- All example data is synthetic — no proprietary or customer files
- No network access required
- No LLM calls during parsing

## Full Test Suite

**132 tests total** (109 existing + 23 new), 3 skipped (Ollama integration):

```bash
mvn test                          # All 132 tests
mvn test -Dllm.integration=true   # Include Ollama integration tests
```
