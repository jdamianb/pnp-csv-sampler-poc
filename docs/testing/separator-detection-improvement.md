# Testing: Separator Detection Improvement

## Test Suite

| Suite | File | Tests | Scope |
|---|---|---|---|
| SeparatorCandidateAnalyzerTest | `SeparatorCandidateAnalyzerTest.java` | 11 | Analyzer accuracy for all 4 delimiter types |

## Test Coverage

| Scenario | Test Name | What It Verifies |
|---|---|---|
| Comma delimiter | `commaDelimiterDetected` | Top candidate is `,` with confidence ≥ 0.5 |
| Semicolon + decimal comma | `semicolonWithDecimalCommaDetected` | Top candidate is `;`, decimal separator is `,` |
| Tab delimiter | `tabDelimiterDetected` | Top candidate is `\t` with confidence ≥ 0.5 |
| Whitespace (KiCad) | `whitespaceDelimiterDetected` | Top candidate is `WHITESPACE` with confidence ≥ 0.5 |
| Metadata not dominating | `metadataDoesNotWinOverSemicolon` | Semicolon wins over WHITESPACE despite metadata |
| Empty sample | `emptySampleReturnsAllZeroConfidence` | All candidates have 0.0 confidence |
| JSON serialization | `analysisToJsonIsValid` | JSON contains `delimiterCandidates` and `recommendedDelimiter` |
| Prompt enrichment | `promptIncludesSeparatorAnalysis` | Prompt builder output contains `DETERMINISTIC SEPARATOR ANALYSIS` |
| Repair prompt (delimiter errors) | `repairPromptIncludesSeparatorOnDelimiterErrors` | Repair prompt includes analysis when delimiter errors present |
| Repair prompt (non-delimiter errors) | `repairPromptSkipsSeparatorOnNonDelimiterErrors` | Repair prompt skips analysis for non-delimiter errors |
| Header row detection | `candidateHeaderRowDetected` | Header row index is correctly identified |

## Running Tests

```bash
# All tests (includes separator analysis)
mvn test

# Separator analysis tests only
mvn test -Dtest=SeparatorCandidateAnalyzerTest

# All 161 tests
mvn test
```

## Verification

### Separator analysis is included in the LLM prompt

```bash
# Run with --no-repair to see raw prompt
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples-extended/01_kicad_native_ascii_mm.pos --no-repair 2>/dev/null | head -5
```

Expected: The output includes `"recommendedDelimiter": "WHITESPACE"` in the separator analysis section.

### Separator analysis is included in the repair prompt

Run with repair enabled on a file that fails (e.g., the KiCad whitespace file):
```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples-extended/01_kicad_native_ascii_mm.pos \
  --llm-provider ollama --llm-url http://localhost:11434 --llm-model qwen2.5:3b 2>/dev/null
```

Check stderr for `[RepairLoop] Repair prompt (first 300 chars):` which should include separator analysis content.

## Test Count

**161 tests total** (158 deterministic, 3 opt-in integration) — all pass with no regressions.
