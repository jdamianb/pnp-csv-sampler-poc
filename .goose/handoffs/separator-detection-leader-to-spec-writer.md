# Handoff: Separator Detection Improvement — Leader to Spec Writer

## Context

Stage 7 evaluation showed that the LLM (qwen2.5:3b) achieves 66.7% parser success, with all 4 failures caused by incorrect delimiter detection:
- whitespace delimiter (KiCad `.pos`) → LLM proposes comma
- semicolon delimiter → LLM proposes comma
- tab delimiter → LLM proposes comma
- empty optional column mappings (partial)

## Goal

Add a deterministic `SeparatorCandidateAnalyzer` that pre-analyzes sampled lines for delimiter candidates (`,`, `;`, `\t`, `WHITESPACE`) and injects the analysis into the LLM prompt and repair prompt as strong evidence — without replacing the LLM.

## Inputs

- `prompt-separator-detection.md` at project root — detailed requirements
- `FormatDetectionPromptBuilder.java` — prompt builder to enrich
- `RepairPromptBuilder.java` — repair prompt builder to enrich  
- `SampleResult.java` — input to the analyzer
- `SimplePnpParser.java` — existing parser for reference

## Expected Outputs

1. **New class**: `SeparatorCandidateAnalyzer` with inner records `SeparatorCandidate`, `SeparatorAnalysis`
2. **Modified**: `FormatDetectionPromptBuilder` — include separator analysis in prompt
3. **Modified**: `RepairPromptBuilder` — include separator analysis in repair prompt when delimiter-related failures occur
4. **Tests**: 5 new test scenarios (comma, semicolon, tab, whitespace, metadata)
5. **Updated**: evaluation harness to include delimiter accuracy metrics

## Constraints

- Must not replace the LLM — only provide deterministic hints
- Must not add Spring Boot, database, or UI
- Must pass `mvn test` without regressions
- Must use only existing dependencies (Jackson for JSON, JUnit 5 for testing)
- Whitespace delimiter: split on one or more spaces/tabs
- Scoring heuristics: consistent column counts, plausible header keywords, X/Y/angle-like fields

## Next

Spec writer creates EARS requirements + Gherkin scenarios. Stop for human approval before implementation.
