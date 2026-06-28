# Prompt: Improve Separator Detection Before Testing More Models

Read this prompt from the repository root.

You are working inside the existing Goose role-based harness.

## Context

Stage 7 evaluation with `qwen2.5:3b` shows good schema compliance but weak delimiter detection.

Baseline:

```text
Total files:       12
Valid JSON:        11 / 12 = 91.7%
Validator Pass:    11 / 12 = 91.7%
Parser Success:     8 / 12 = 66.7%
Config Accuracy:    8 / 12 = 66.7%
Repair Attempted:   5
Repair Successful:  2
Repair Failed:      3
```

Known failures:

```text
01_kicad_native_ascii_mm.pos
  Root cause: whitespace delimiter not detected.
  LLM proposed comma delimiter.

03_jlcpcb_cpl_minimal.csv
  Root cause: empty optional column mappings / weak config completion.
  LLM leaves partNumber/jedec with empty values.

08_machine_semicolon_decimal_comma.csv
  Root cause: semicolon delimiter not detected.
  LLM proposed comma delimiter.

09_machine_tab_separated_with_metadata.tsv
  Root cause: tab delimiter not detected.
  LLM proposed comma delimiter.
```

Main pattern:

```text
The LLM defaults to comma delimiter and does not reliably detect non-comma delimiters.
```

## Goal

Before testing other models or starting fine-tuning, improve separator/delimiter detection using deterministic pre-analysis.

The goal is not to replace the LLM.

The goal is to provide strong delimiter/header hints to the LLM so it does not guess blindly.

Target workflow:

```text
PnP file
  ↓
CsvSampler
  ↓
SeparatorCandidateAnalyzer
  ↓
Header/data candidate analysis
  ↓
Enriched LLM prompt
  ↓
PnpImportFormatConfig
  ↓
Validator
  ↓
Simple parser dry-run
  ↓
Repair loop if needed
```

## Scope

Implement deterministic separator/delimiter pre-analysis and include it in the LLM prompt and repair prompt.

Allowed:

- delimiter candidate analyzer
- header candidate analyzer
- column-count statistics
- prompt enrichment
- repair-prompt enrichment
- evaluation metrics for delimiter/header/data-start accuracy
- tests for comma, semicolon, tab, and whitespace

Out of scope:

- testing new models
- fine-tuning
- RAG
- UI
- database
- production parser rewrite
- proprietary parser behavior
- proprietary data
- asking the LLM to parse placement rows directly

## Required new concept: SeparatorCandidateAnalyzer

Create a deterministic analyzer that inspects sampled raw lines.

Suggested class names:

```text
SeparatorCandidateAnalyzer
SeparatorCandidate
SeparatorAnalysis
HeaderCandidate
```

Use the existing package style.

Do not over-engineer.

## Candidate delimiters

Evaluate at least:

```text
,
;
\t
WHITESPACE
```

Optional later:

```text
|
```

Do not add many exotic delimiters in this stage.

## Candidate statistics

For each candidate delimiter, compute as many of these as reasonable:

```text
delimiter
nonEmptyAnalyzedLines
linesWithAtLeastTwoColumns
maxColumnCount
averageColumnCount
mostCommonColumnCount
mostCommonColumnCountFrequency
columnCountConsistencyRatio
candidateHeaderRowIndex
candidateDataStartRowIndex
sampleHeaderColumns
likelyDecimalSeparator
confidence
warnings
```

Example JSON to inject into the prompt:

```json
{
  "delimiterCandidates": [
    {
      "delimiter": ";",
      "nonEmptyAnalyzedLines": 8,
      "linesWithAtLeastTwoColumns": 5,
      "maxColumnCount": 9,
      "averageColumnCount": 9.0,
      "mostCommonColumnCount": 9,
      "mostCommonColumnCountFrequency": 5,
      "columnCountConsistencyRatio": 1.0,
      "candidateHeaderRowIndex": 5,
      "candidateDataStartRowIndex": 6,
      "sampleHeaderColumns": [
        "No.",
        "RefDes",
        "PartNo",
        "Package",
        "X-Pos",
        "Y-Pos",
        "Angle",
        "MountSide",
        "Feeder"
      ],
      "likelyDecimalSeparator": ",",
      "confidence": 0.94,
      "warnings": []
    }
  ]
}
```

## Recommended scoring heuristics

A good delimiter candidate should:

- split several lines into at least 4 columns
- produce consistent column counts across likely data rows
- have a plausible header row
- have at least one likely X column
- have at least one likely Y column
- have at least one likely angle/rotation column
- have at least one identity-like column such as reference/designator/part/PN/package

Use generic PnP/CPL header keywords.

Reference keywords:

```text
ref, refdes, designator, design, component, item
```

Part number keywords:

```text
part, partno, pn, partnumber, componentid, value, comment
```

Package / JEDEC keywords:

```text
package, footprint, jedec, pattern
```

X keywords:

```text
x, posx, x-pos, x_pos, mid x, midx, centerx, x(mm), x (mm)
```

Y keywords:

```text
y, posy, y-pos, y_pos, mid y, midy, centery, y(mm), y (mm)
```

Angle keywords:

```text
angle, rotation, rot, theta, r(deg), rotate
```

Side keywords:

```text
side, layer, mountside, boardside
```

Do not make these heuristics proprietary or company-specific.

## Whitespace delimiter rules

Whitespace is special.

For `WHITESPACE`, split on one or more spaces/tabs.

Do not treat arbitrary metadata lines as strong whitespace-table evidence.

A whitespace candidate should score well only when:

- several consecutive lines have a consistent number of whitespace-separated tokens
- there is a plausible header line
- the header contains fields such as Ref, Val, Package, PosX, PosY, Rot, Side
- data rows contain numeric X/Y/angle-like fields

This is important for KiCad `.pos` style files.

## Tab delimiter rules

A tab delimiter should be strongly preferred when raw lines contain actual tab characters and split into consistent columns.

A `.tsv` extension may be used as a weak hint, but content must be the primary signal.

## Semicolon + decimal comma rules

A semicolon delimiter should be strongly preferred when:

- semicolon splits produce many consistent columns
- comma splits produce inconsistent or misleading fragments
- numeric values contain comma decimals like `12,500`

If semicolon is the best delimiter and numeric-looking values contain comma decimals, set likely decimal separator to `,`.

## Prompt enrichment

Update `FormatDetectionPromptBuilder` so the LLM receives the separator analysis.

Add a section like:

```text
DETERMINISTIC SEPARATOR ANALYSIS:
The following candidates were computed before calling the LLM.
Use these candidates as strong evidence.
Do not default to comma if another candidate has much higher confidence.

<JSON separator analysis here>
```

The prompt should explicitly say:

```text
If the top-ranked delimiter candidate has high confidence and is consistent with the sampled rows, use it unless there is clear contradictory evidence.
```

It should also say:

```text
For TSV files, use "\t" as delimiter.
For whitespace-delimited files, use "WHITESPACE" as delimiter.
For semicolon files with decimal comma, use ";" as delimiter and "," as decimalSeparator.
```

## Repair prompt enrichment

If parser dry-run fails because the delimiter appears wrong, include separator analysis in the repair prompt.

Example:

```text
The previous config failed parser dry-run.

The config used delimiter ",".

However, deterministic separator analysis ranked ";" highest:
- stable rows: 5
- common column count: 9
- confidence: 0.94
- candidate header row: 5

Return a corrected config JSON only.
```

## Tests to add

Add tests for the analyzer.

### Test 1: comma CSV

Input:

```csv
Designator,Mid X,Mid Y,Layer,Rotation
C1,95.0518mm,22.6822mm,Top,270
```

Expected:

```text
top delimiter = ","
```

### Test 2: semicolon + decimal comma

Input:

```csv
No.;RefDes;PartNo;Package;X-Pos;Y-Pos;Angle;MountSide;Feeder
1;R1;RES-10K-0603;0603;12,500;33,100;90;T;F01
```

Expected:

```text
top delimiter = ";"
likely decimal separator = ","
```

### Test 3: tab-separated file

Input:

```text
Index\tDesignator\tPN\tJEDEC\tCenterX\tCenterY\tTheta\tBoardSide
1\tR1\tRES-10K-0603\t0603\t12.500\t33.100\t90\tTOP
```

Expected:

```text
top delimiter = "\t"
```

### Test 4: whitespace KiCad-like file

Input:

```text
# Ref Val Package PosX PosY Rot Side
C1 100n C_0603_1608Metric 95.0518 22.6822 270.0 top
R1 10k R_0603_1608Metric 106.4056 23.0124 90.0 top
```

Expected:

```text
top delimiter = "WHITESPACE"
candidate header row is the row containing Ref/Val/Package/PosX/PosY/Rot/Side
```

### Test 5: metadata should not win

Input:

```text
Job Name: BOARD-1234
Generated: 2026-06-27
Machine: Synthetic-PnP-9000
No.;RefDes;PartNo;Package;X-Pos;Y-Pos;Angle;MountSide;Feeder
1;R1;RES-10K-0603;0603;12,500;33,100;90;T;F01
```

Expected:

```text
top delimiter = ";"
not WHITESPACE
```

## Evaluation updates

Update the Stage 7 evaluation harness to support comparison before/after separator analysis.

Add metrics:

```text
Delimiter Accuracy
Header Row Accuracy
Data Start Row Accuracy
Decimal Separator Accuracy
```

The report should show whether the separator hints fix:

```text
01_kicad_native_ascii_mm.pos
08_machine_semicolon_decimal_comma.csv
09_machine_tab_separated_with_metadata.tsv
```

## Expected impact

Target improvement:

```text
Parser Success: from 66.7% to at least 83.3%
Config Accuracy: from 66.7% to at least 83.3%
```

Stretch goal:

```text
Parser Success: 91.7% or better
Config Accuracy: 91.7% or better
```

## Acceptance criteria

This improvement is complete when:

- `mvn test` passes.
- Existing sampler tests still pass.
- Existing detector tests still pass.
- Existing parser tests still pass.
- Existing repair-loop tests still pass.
- New `SeparatorCandidateAnalyzer` tests pass.
- The LLM prompt includes deterministic separator analysis.
- The repair prompt includes separator analysis when delimiter-related failures occur.
- Stage 7 evaluation report includes delimiter accuracy metrics.
- The 12-file evaluation can be rerun with `qwen2.5:3b`.
- Results are compared against the current baseline.
- No new model testing is required for this change.
- No fine-tuning is implemented in this change.

## Suggested Goose start prompt

```text
Read prompt-separator-detection.md and follow the existing role workflow.

We are not testing new models yet.

We are improving delimiter/separator detection before model comparison or fine-tuning.

The current qwen2.5:3b evaluation shows that failures are concentrated on non-comma delimiter detection.

Implement deterministic separator candidate analysis and inject it into the LLM prompt and repair prompt.

First, the spec writer must create EARS requirements and Gherkin scenarios.
Stop for my approval before implementation.
```
