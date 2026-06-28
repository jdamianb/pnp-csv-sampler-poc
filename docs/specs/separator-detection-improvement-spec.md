# Specification: Deterministic Separator Detection Improvement

## 1. Purpose

Add deterministic separator/delimiter pre-analysis to the LLM-assisted format detection pipeline. The analysis provides strong delimiter/header hints to the LLM so it does not guess blindly, improving delimiter detection for non-comma delimiters (whitespace, semicolon, tab).

## 2. Scope

### In scope
- `SeparatorCandidateAnalyzer` class with deterministic delimiter analysis
- Prompt enrichment in `FormatDetectionPromptBuilder`
- Repair-prompt enrichment in `RepairPromptBuilder`
- Tests for all 4 delimiter types (comma, semicolon, tab, whitespace)
- Updated evaluation harness with delimiter accuracy metrics
- Updated evaluation report with before/after comparison

### Out of scope
- Testing new LLM models
- Fine-tuning
- RAG
- UI
- Database
- Proprietary parser behavior or data
- Production deployment

## 3. EARS Requirements

### 3.1 Ubiquitous requirements

| ID | Requirement |
|---|---|
| R01 | The system SHALL analyze all 4 candidate delimiters (`,`, `;`, `\t`, `WHITESPACE`) for each sampled file |
| R02 | The system SHALL compute, for each candidate delimiter, a confidence score based on column count consistency |
| R03 | The system SHALL identify a candidate header row index for each delimiter based on the first row with consistent column count |
| R04 | The system SHALL identify candidate data start rows based on the first row after the header with matching column count |
| R05 | The system SHALL detect the likely decimal separator (`.` or `,`) for each delimiter candidate |
| R06 | The system SHALL generate a list of sample header column names for each delimiter candidate |
| R07 | The separator analysis SHALL be serialized as JSON and included in the LLM prompt |

### 3.2 Event-driven requirements

| ID | Requirement |
|---|---|
| R08 | WHEN the candidate delimiter confidence exceeds 0.7, THEN the prompt SHALL instruct the LLM to strongly consider it |
| R09 | WHEN the candidate delimiter is whitespace with consistent columns and plausible header keywords, THEN the prompt SHALL recommend WHITESPACE over comma |
| R10 | WHEN the candidate delimiter is tab with actual tab characters, THEN the prompt SHALL recommend `\t` over comma |
| R11 | WHEN semicolon splits produce consistent columns and comma splits produce inconsistent fragments, THEN the prompt SHALL recommend `;` |
| R12 | WHEN semicolon is preferred and numeric values contain comma decimals, THEN the prompt SHALL set decimal separator to `,` |
| R13 | WHEN the repair loop encounters delimiter-related failures, THEN the separator analysis SHALL be included in the repair prompt |

### 3.3 State-driven requirements

| ID | Requirement |
|---|---|
| R14 | WHILE computing separator candidates, the system SHALL ignore empty lines and metadata lines that do not contain delimiters |
| R15 | WHILE scoring candidates, the system SHALL prefer delimiters that split multiple consecutive lines into consistent column counts |
| R16 | WHILE scoring whitespace candidates, the system SHALL only score well when several consecutive lines have a consistent number of whitespace-separated tokens |
| R17 | WHILE building the prompt, the separator analysis SHALL be placed after the schema instruction and before the sample lines |

### 3.4 Unwanted behavior requirements

| ID | Requirement |
|---|---|
| R18 | IF no delimiter candidate achieves minimum confidence (0.3), THEN comma SHALL be the fallback default |
| R19 | IF the separator analysis fails (e.g., no lines), THEN the prompt SHALL omit the analysis section and proceed with the default prompt |
| R20 | IF the separator analysis finds no consistent columns, THEN all candidates SHALL report confidence 0.0 |

### 3.5 Feature requirements

| ID | Requirement |
|---|---|
| R21 | The `SeparatorCandidateAnalyzer` SHALL provide a public method `analyze(SampleResult)` returning `SeparatorAnalysis` |
| R22 | The `SeparatorCandidate` record SHALL include: delimiter, nonEmptyAnalyzedLines, linesWithAtLeastTwoColumns, maxColumnCount, averageColumnCount, mostCommonColumnCount, mostCommonColumnCountFrequency, columnCountConsistencyRatio, candidateHeaderRowIndex, candidateDataStartRowIndex, sampleHeaderColumns, likelyDecimalSeparator, confidence, warnings |
| R23 | The `SeparatorAnalysis` record SHALL include: candidates (list of `SeparatorCandidate`), recommendedDelimiter, recommendedDecimalSeparator |
| R24 | The scoring heuristics SHALL use standard PnP header keywords for column matching |
| R25 | The prompt SHALL include an instruction: "If the top-ranked delimiter candidate has high confidence and is consistent with the sampled rows, use it unless there is clear contradictory evidence." |

## 4. Scoring Heuristics

Confidence is calculated based on:

1. **Column count consistency**: percentage of data rows sharing the same column count (0-1 weight)
2. **Minimum columns**: candidate must split at least some lines into ≥4 columns
3. **Header presence**: a row exists with plausible PnP header keywords
4. **Keyword match**: header contains reference-like, X-like, Y-like, angle-like keywords

### Reference keywords: `ref`, `refdes`, `designator`, `design`, `component`, `item`
### Part number keywords: `part`, `partno`, `pn`, `partnumber`, `componentid`, `value`, `comment`
### Package keywords: `package`, `footprint`, `jedec`, `pattern`
### X keywords: `x`, `posx`, `x-pos`, `x_pos`, `mid x`, `midx`, `centerx`, `x(mm)`, `x (mm)`
### Y keywords: `y`, `posy`, `y-pos`, `y_pos`, `mid y`, `midy`, `centery`, `y(mm)`, `y (mm)`
### Angle keywords: `angle`, `rotation`, `rot`, `theta`, `r(deg)`, `rotate`
### Side keywords: `side`, `layer`, `mountside`, `boardside`

## 5. Gherkin Scenarios

### 5.1 Comma delimiter
```gherkin
Scenario: Comma-delimited CSV file is correctly identified
  Given a sample with comma-separated lines like "Designator,Mid X,Mid Y,Layer,Rotation"
  When the separator analyzer runs
  Then the top candidate SHALL be ","
  And the confidence SHALL be >= 0.5
```

### 5.2 Semicolon delimiter with decimal comma
```gherkin
Scenario: Semicolon-delimited file with decimal comma is correctly identified
  Given a sample with semicolon-separated lines like "1;R1;RES-10K-0603;0603;12,500;33,100;90;T;F01"
  When the separator analyzer runs
  Then the top candidate SHALL be ";"
  And the likely decimal separator SHALL be ","
```

### 5.3 Tab delimiter
```gherkin
Scenario: Tab-delimited file is correctly identified
  Given a sample with tab-separated lines like "Index\tDesignator\tPN\tJEDEC\tCenterX\tCenterY\tTheta\tBoardSide"
  When the separator analyzer runs
  Then the top candidate SHALL be "\t"
  And the confidence SHALL be >= 0.5
```

### 5.4 Whitespace delimiter (KiCad)
```gherkin
Scenario: Whitespace-delimited KiCad file is correctly identified
  Given a sample with space-separated lines like "C1 100n C_0603_1608Metric 95.0518 22.6822 270.0 top"
  And a header line "Ref Val Package PosX PosY Rot Side"
  When the separator analyzer runs
  Then the top candidate SHALL be "WHITESPACE"
  And the candidate header row SHALL be the row containing Ref/Val/Package/PosX/PosY/Rot/Side
```

### 5.5 Metadata should not win
```gherkin
Scenario: Metadata lines do not distort separator analysis
  Given a sample with metadata lines like "Job Name: BOARD-1234" followed by semicolon-delimited data
  When the separator analyzer runs
  Then the top candidate SHALL be ";"
  And WHITESPACE SHALL NOT be the top candidate
```

### 5.6 Prompt enrichment
```gherkin
Scenario: LLM prompt includes separator analysis
  Given a valid sample
  When the prompt builder builds the prompt
  Then the prompt SHALL contain "DETERMINISTIC SEPARATOR ANALYSIS"
  And the prompt SHALL contain the separator analysis JSON
  And the prompt SHALL include delimiter-specific recommendations
```

### 5.7 Repair prompt enrichment
```gherkin
Scenario: Repair prompt includes separator analysis on delimiter errors
  Given a failed config with wrong delimiter
  When the repair prompt builder builds the repair prompt
  Then the repair prompt SHALL include the separator analysis
  And the repair prompt SHALL reference the wrong delimiter issue
```

### 5.8 No separator analysis available
```gherkin
Scenario: Empty sample skips separator analysis
  Given an empty sample with no lines
  When the separator analyzer runs
  Then all candidates SHALL have confidence 0.0
```

## 6. Prompt Format

### Detection prompt enrichment

After the schema instruction and before the sample lines, add:

```
DETERMINISTIC SEPARATOR ANALYSIS:
The following candidates were computed before calling the LLM.
Use these candidates as strong evidence.
Do not default to comma if another candidate has much higher confidence.

<JSON separator analysis>

If the top-ranked delimiter candidate has high confidence and is consistent with the sampled rows, use it unless there is clear contradictory evidence.
For TSV files, use "\t" as delimiter.
For whitespace-delimited files, use "WHITESPACE" as delimiter.
For semicolon files with decimal comma, use ";" as delimiter and "," as decimalSeparator.
```

### Repair prompt enrichment

When delimiter-related errors occur, add after the error section:

```
DETERMINISTIC SEPARATOR ANALYSIS:
<JSON separator analysis>

The previous config used delimiter "<wrong>".
Deterministic analysis ranked "<correct>" highest.
```

## 7. Evaluation Updates

Add the following metrics to `scripts/run-evaluation.sh`:

| Metric | Description |
|---|---|
| Delimiter Accuracy | Percentage of files where delimiter was correctly detected |
| Header Row Accuracy | Percentage of files where header row index was correct |
| Data Start Row Accuracy | Percentage of files where data start row index was correct |
| Decimal Separator Accuracy | Percentage of files where decimal separator was correct |

Update the evaluation report to show before/after comparison for the 4 previously failing files.

## 8. IntelliJ Configurations

The existing evaluation configurations remain unchanged.

## 9. Human Review Checklist

- [ ] SeparatorCandidateAnalyzer correctly identifies all 4 delimiter types
- [ ] Confidence scoring is reasonable (not all 1.0 or all 0.0)
- [ ] Prompt includes separator analysis section
- [ ] Repair prompt includes separator analysis on delimiter errors
- [ ] All 5 test scenarios pass
- [ ] All existing tests still pass (150, no regressions)
- [ ] Evaluation harness includes delimiter accuracy metrics
- [ ] No proprietary code or data added
