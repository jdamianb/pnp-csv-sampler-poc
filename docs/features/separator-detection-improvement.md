# Separator Detection Improvement

## Summary

A deterministic `SeparatorCandidateAnalyzer` that pre-analyzes sampled CSV lines for delimiter candidates (`,`, `;`, `\t`, `WHITESPACE`) before calling the LLM. The analysis is injected into the LLM prompt and repair prompt as strong evidence, helping the model make informed delimiter decisions instead of guessing blindly.

## Status

✅ Implemented and tested (added to Stage 2/3 pipeline).

## Architecture

```
PnP file → Sampler → SampleResult
                         ↓
              SeparatorCandidateAnalyzer
                         ↓
              SeparatorAnalysis (JSON)
                         ↓
              FormatDetectionPromptBuilder
                         ↓
              Enriched prompt → LLM → Config
                         ↓
              (if repair needed)
              RepairPromptBuilder includes separator analysis
```

## Source Files

| File | Lines | Description |
|---|---|---|
| `SeparatorCandidateAnalyzer.java` | 408 | Deterministic delimiter analysis with confidence scoring |
| `FormatDetectionPromptBuilder.java` (modified) | 89 | Prompt now includes separator analysis section |
| `RepairPromptBuilder.java` (modified) | 70 | Repair prompt includes analysis on delimiter errors |

## How It Works

### Candidate Delimiters

1. `,` (comma)
2. `;` (semicolon)
3. `\t` (tab)
4. `WHITESPACE` (one or more spaces/tabs)

### Per-Candidate Statistics

For each delimiter, the analyzer computes:

- `nonEmptyAnalyzedLines` — lines with content
- `linesWithAtLeastTwoColumns` — lines split into ≥2 columns
- `maxColumnCount`, `averageColumnCount` — column count stats
- `mostCommonColumnCount`, `mostCommonColumnCountFrequency` — dominant column pattern
- `columnCountConsistencyRatio` — fraction of lines matching the dominant count (0-1)
- `candidateHeaderRowIndex`, `candidateDataStartRowIndex` — detected structural rows
- `sampleHeaderColumns` — header column names (lowercase, capitalized)
- `likelyDecimalSeparator` — `.` or `,` detected from numeric values
- `confidence` — overall confidence score (0-1)

### Confidence Scoring

| Factor | Weight | Description |
|---|---|---|
| Column consistency | 0.4 | Percentage of lines sharing the most common column count |
| Minimum columns | 0.2 | ≥4 columns gets full bonus, ≥2 gets partial |
| Data coverage | 0.1 | Ratio of analyzed lines with ≥2 columns |
| Header keywords | 0.25 | Reference, X, Y, and angle keyword matches |
| Whitespace penalty | -0.2 | Applied when whitespace header lacks KiCad-like fields |

### Header Keyword Groups

| Group | Keywords |
|---|---|
| Ref | ref, refdes, designator, design, component, item |
| Part | part, partno, pn, partnumber, componentid, value, comment |
| Package | package, footprint, jedec, pattern |
| X | x, posx, x-pos, x_pos, mid x, midx, centerx, x(mm), x (mm) |
| Y | y, posy, y-pos, y_pos, mid y, midy, centery, y(mm), y (mm) |
| Angle | angle, rotation, rot, theta, r(deg), rotate |
| Side | side, layer, mountside, boardside |

## Prompt Enrichment

The detection prompt now includes a `DETERMINISTIC SEPARATOR ANALYSIS:` section with the JSON analysis output, plus instructions:

> *"If the top-ranked delimiter candidate has high confidence and is consistent with the sampled rows, use it unless there is clear contradictory evidence."*
> *"For TSV files, use `\t` as delimiter."*
> *"For whitespace-delimited files, use `WHITESPACE` as delimiter."*
> *"For semicolon files with decimal comma, use `;` as delimiter and `,` as decimalSeparator."*

## Separator Analysis Verification

The analyzer correctly identifies all 4 delimiter types when tested directly:

| File | Expected | Analyzer Result | Confidence |
|---|---|---|---|
| `01_kicad_native_ascii_mm.pos` | WHITESPACE | 🟢 WHITESPACE | 0.77 |
| `02_kicad_csv_ref_val_package_pos.csv` | `,` | 🟢 `,` | 0.95 |
| `08_machine_semicolon_decimal_comma.csv` | `;` | 🟢 `;` | 0.90 |
| `09_machine_tab_separated_with_metadata.tsv` | `\t` | 🟢 `\t` | 0.91 |

## Known Limitation

The `qwen2.5:3b` model does not consistently follow the separator analysis hints for non-comma delimiters despite the analysis being correct. A larger model (7B+) would likely benefit from these hints.

## Tests

11 new tests in `SeparatorCandidateAnalyzerTest.java` covering:
- Comma delimiter detection
- Semicolon with decimal comma detection
- Tab delimiter detection
- Whitespace (KiCad) delimiter detection
- Metadata lines not dominating analysis
- Empty sample handling
- JSON serialization
- Prompt content verification
- Repair prompt enrichment (with and without delimiter errors)
- Header row index detection
