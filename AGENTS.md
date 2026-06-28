# AGENTS.md

This project uses a role-based Goose agent harness.

All agents must read this file before editing code, specs, tests, examples, or documentation.

## Project goal

Build a Java 21 proof of concept that reduces the manual setup required to import Pick-and-Place files.

Today, a user manually tells the system how to read a PnP CSV/TSV-like file:

- which lines to ignore
- which row contains the header
- where data starts and ends
- which column is reference/designator
- which column is part number
- which column is JEDEC/package/footprint
- which column is X
- which column is Y
- which column is angle/rotation
- which column is side/layer, if available
- which decimal separator is used
- which units are used

The PoC goal is to automate as much of that manual import configuration as possible.

The LLM must act as a configuration assistant, not as the parser.

For this public PoC, the parser must be an original, simple parser implemented in this repository. Do not use proprietary company parser code, proprietary parser behavior, proprietary test data, or proprietary file specifications.

## Product workflow target

The target PoC workflow is:

```text
PnP CSV/TSV-like file
  ↓
CSV sampler
  ↓
Numbered raw sample JSON
  ↓
LLM-assisted format detector
  ↓
Proposed PnP import format config JSON
  ↓
Config validation
  ↓
Simple open PoC parser dry-run
  ↓
Optional repair loop
  ↓
Human review / accept / edit
  ↓
Simple parser parses normalized placement preview
```

## Core design rules

### 1. The sampler is dumb and deterministic

The sampler must only read raw text and preserve line indexes.

The sampler must not:

- parse CSV fields
- infer delimiter
- infer header row
- infer column mappings
- infer units
- normalize decimals
- trim line content
- call any LLM API
- use Spring Boot

### 2. The detector proposes configuration only

The detector may infer:

- delimiter
- quote character
- encoding, when applicable
- lines to ignore
- header row index
- data start row index
- data end row index
- decimal separator
- column mappings
- coordinate units
- side/layer value mappings
- warnings and confidence

The detector must not:

- parse the full file into placement rows
- rewrite the CSV
- invent component data
- replace the parser
- persist production formats unless explicitly requested

### 3. The Stage 4 parser is simple, open, and original

Because the real company parser is proprietary and not available in this repository, Stage 4 must implement a small original parser for the PoC.

The Stage 4 parser must:

- consume a `PnpImportFormatConfig`
- read a PnP CSV/TSV-like input file
- apply ignored lines and data row bounds
- resolve mappings by header name or column index
- parse required fields into a small normalized placement DTO
- validate that X, Y, and angle are numeric
- produce a dry-run report with rows parsed, rows rejected, errors, warnings, and sample normalized rows

The Stage 4 parser must not:

- copy or mimic proprietary company parser internals
- use proprietary company code
- use proprietary company test data
- claim production compatibility
- support every real machine format
- perform LLM inference itself
- call a real LLM
- use Spring Boot

### 4. Tests must be deterministic

Unit tests must not require:

- network access
- API keys
- a running local LLM
- a cloud LLM
- external services
- proprietary company files

Real LLM tests must be opt-in integration tests.

Parser tests must use synthetic examples only.

## Documentation reference

Feature documentation is stored under `docs/` with the feature name as the file name.

### Stage 1 — CSV Sampler

- `docs/feature-requests/pnp-csv-sampler-poc.md`
- `docs/specs/pnp-csv-sampler-spec.md`
- `docs/features/pnp-csv-sampler.md`
- `docs/testing/pnp-csv-sampler.md`

### Stage 2 — LLM-assisted Format Detection

- `docs/feature-requests/llm-assisted-pnp-format-detection.md`
- `docs/specs/llm-assisted-pnp-format-detection-spec.md`
- `docs/features/llm-assisted-pnp-format-detection.md`
- `docs/testing/llm-assisted-pnp-format-detection.md`
- `expected-configs/`

### Stage 3 — Real LLM Adapter

- `docs/feature-requests/stage3-real-llm-adapter.md`
- `docs/specs/stage3-real-llm-adapter-spec.md`
- Stage 3 may be documented inside `docs/features/llm-assisted-pnp-format-detection.md` or its own file.

### Stage 4 — Simple Open PnP Parser Dry-run

- `docs/feature-requests/stage4-simple-pnp-parser-dry-run.md`
- `docs/specs/stage4-simple-pnp-parser-dry-run-spec.md`
- `docs/features/stage4-simple-pnp-parser-dry-run.md`
- `docs/testing/stage4-simple-pnp-parser-dry-run.md`
- `docs/adr/0004-simple-open-pnp-parser-for-poc.md`

Agents should read the relevant docs before working on a feature.

## PoC stages

Each stage is a separate feature and must go through the role workflow.

### Stage 1: CSV sampler

Status: implemented.

Goal:

```text
PnP file in
  ↓
raw numbered sample JSON out
```

Allowed:

- Java 21
- Maven
- Jackson
- JUnit 5
- CLI command for sampling
- synthetic example PnP files
- documentation and tests

Forbidden in Stage 1:

- LLM calls
- format detection
- parser integration
- Spring Boot

### Stage 2: LLM-assisted format detection contract

Status: implemented.

Goal:

```text
CsvSample JSON
  ↓
prompt builder
  ↓
LLM client abstraction
  ↓
PnpImportFormatConfig JSON
  ↓
validator
```

Allowed:

- `PnpImportFormatConfig`
- `ColumnMapping`
- `ColumnSource`
- `FormatDetectionPromptBuilder`
- `PnpImportFormatConfigValidator`
- `LlmClient` abstraction
- `StubLlmClient`
- `detect <file>` CLI command
- deterministic tests

### Stage 3: Real LLM adapter

Status: implemented or in testing.

Goal:

```text
CsvSample JSON
  ↓
real local or cloud LLM
  ↓
PnpImportFormatConfig JSON
```

Allowed only after explicit human approval:

- Ollama-compatible local client
- OpenAI-compatible local client, for example LM Studio, llama.cpp server, or vLLM
- DeepSeek/OpenAI-compatible cloud client for comparison
- integration tests disabled by default

Required safety rules:

- no API keys committed
- network tests must be opt-in
- unit tests must still pass without network
- provider selection must be configurable
- temperature should be deterministic, usually `0`

### Stage 4: Simple open PnP parser dry-run

Status: next stage.

Goal:

```text
PnP file + PnpImportFormatConfig
  ↓
simple original parser
  ↓
PnpParseDryRunReport
```

Allowed:

- original simple parser implementation
- config-driven parsing
- header-name and column-index mapping
- comma, semicolon, tab, and simple whitespace delimiters
- quote handling for common CSV cases
- decimal separator normalization for `.` and `,`
- coordinate suffix cleanup, such as `12.3mm`
- side value mapping
- row-level errors and warnings
- `parse` or `dry-run` CLI command
- deterministic tests using synthetic examples

Forbidden:

- proprietary company parser code
- proprietary company parser behavior copied from memory or internal docs
- proprietary company data
- production claims
- LLM-based row parsing
- Spring Boot
- database
- UI

### Stage 5: Repair loop

Goal:

```text
LLM proposed config
  ↓
validator/parser dry-run error
  ↓
repair prompt
  ↓
corrected config
```

Allowed only after Stage 4 exists:

- bounded repair loop
- maximum retry count, usually 2 or 3
- repair prompt containing validation/parser errors
- final fallback to human correction

Required rule:

The repair loop must stop and expose the failure clearly if it cannot produce a valid configuration.

### Stage 6: Human review and correction flow

Goal:

```text
proposed config
  ↓
human accept/edit/reject
```

Allowed:

- CLI review output
- Markdown/JSON report
- future UI feature request, if explicitly approved
- storing corrected examples for later evaluation, if explicitly approved

### Stage 7: Evaluation and dataset building

Goal:

Measure whether the PoC reduces manual import setup.

Allowed:

- evaluation folder
- representative synthetic sample files
- expected config JSON files
- expected normalized placement JSON files
- accuracy report
- manual correction count
- time-saved notes

Metrics should include:

- valid JSON rate
- validator pass rate
- parser dry-run success rate
- number of manual corrections
- files fully solved without correction
- files requiring fallback

### Stage 8: Future fine-tuning investigation

Fine-tuning is not part of the current PoC.

It may be investigated only after enough labeled examples exist:

```text
CsvSample JSON → approved PnpImportFormatConfig JSON
```

## Roles

Role definitions are stored under `.goose/roles/`.

- Leader: `.goose/roles/leader.md`
- Spec Writer: `.goose/roles/spec-writer.md`
- Implementer: `.goose/roles/implementer.md`
- Reviewer: `.goose/roles/reviewer.md`
- Doc Writer: `.goose/roles/doc-writer.md`

## Role responsibilities

### Leader / Orchestrator

The leader coordinates the workflow.

Responsibilities:

- read this file before acting
- identify the current PoC stage
- select the correct feature request
- hand off to the correct role
- enforce human gates
- prevent scope creep
- prevent roles from skipping phases
- stop when human approval is required
- summarize progress and next steps

The leader must not implement code unless explicitly instructed by the human.

### Spec Writer

The spec writer converts a feature request into implementation-neutral specs.

Outputs:

- EARS requirements
- Gherkin acceptance scenarios
- scope and out-of-scope list
- assumptions
- validation rules
- testability notes
- human review checklist

The spec writer must not implement code.

### Implementer

The implementer writes code only from an approved spec.

Responsibilities:

- implement the smallest correct solution
- keep dependencies minimal
- preserve existing behavior
- write tests
- run tests
- produce an implementation handoff

The implementer must not expand scope without leader and human approval.

### Reviewer

The reviewer checks implementation quality and scope compliance.

Responsibilities:

- run or request test execution
- verify the approved spec is satisfied
- check code quality
- check deterministic tests
- reject scope creep
- loop with the implementer until accepted

The reviewer must reject implementations that violate core design rules.

For Stage 4, the reviewer must explicitly check that no proprietary parser code, examples, or behavior were added.

### Doc Writer

The doc writer updates documentation only after implementation approval.

Responsibilities:

- update `docs/`
- update `README.md`
- update `AGENTS.md` only when workflow or scope changed
- document build/test/run commands
- document limitations and next steps

The doc writer must not change production code unless explicitly instructed.

## Standard feature workflow

Every feature or stage must follow this workflow.

### Phase 1: Leader intake

The leader reads:

- `AGENTS.md`
- relevant role files
- relevant feature request under `docs/feature-requests/`
- current docs and reviews, when relevant

The leader creates a handoff under:

```text
.goose/handoffs/
```

### Phase 2: Specification

The spec writer creates or updates a spec under:

```text
docs/specs/
```

The spec must include:

- EARS requirements
- Gherkin scenarios
- out-of-scope list
- acceptance criteria
- human review checklist

The leader must stop for human approval.

Implementation cannot begin before human approval.

### Phase 3: Implementation

The implementer reads only approved specs and relevant handoffs.

The implementer writes code and tests.

The implementer must run:

```bash
mvn test
```

If packaging is configured, the implementer may also run:

```bash
mvn clean package
```

The implementer creates a handoff under:

```text
.goose/handoffs/
```

### Phase 4: Review loop

The reviewer checks:

- code quality
- scope compliance
- tests
- Gherkin coverage
- CLI behavior, when applicable
- no overengineering
- no forbidden features

The reviewer and implementer loop until:

- tests pass
- quality is acceptable
- scope is respected

The reviewer writes a report under:

```text
.goose/reviews/
```

The leader must stop for human implementation approval.

### Phase 5: Documentation

After human implementation approval, the doc writer updates:

- `docs/`
- `README.md`
- `AGENTS.md`, only if the harness or workflow changed

The doc writer creates a completion handoff under:

```text
.goose/handoffs/
```

The leader must stop for final human approval.

## Human gates

The leader must stop and request human review when:

1. Specs are drafted.
2. Reviewer accepts implementation.
3. Documentation is updated.
4. A stage wants to introduce a real LLM call.
5. A stage wants to integrate or replace parser behavior.
6. A stage wants to add persistence, UI, fine-tuning, or RAG.

Use this wording:

```text
Human review required before continuing.
Please approve, reject, or request changes.
```

## Required commands

Default command:

```bash
mvn test
```

Package command, when applicable:

```bash
mvn clean package
```

Sampler command, when applicable:

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar sample examples/simple-pnp.csv
```

Detector command, when applicable:

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv
```

Stage 4 parser command, when applicable:

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse examples/simple-pnp.csv expected-configs/simple-pnp.config.json
```

If the project uses a different invocation style, update this section and the README together.

## Configuration and secrets

Agents must not commit:

- API keys
- tokens
- passwords
- private URLs
- proprietary sample files
- customer data

Provider configuration must use:

- environment variables
- local ignored config files
- command-line arguments
- documented placeholders

## Data safety and IP boundaries

Agents must not add real company/customer PnP files to the public repository.

Agents must not use proprietary company parser code, proprietary parser behavior, proprietary sample files, or internal documentation.

Use synthetic examples unless the human explicitly provides approved public samples.

If there is uncertainty about whether an example, behavior, or format detail is proprietary, stop and ask the human.

## Full PoC completion criteria

The full PoC is complete only when:

- the sampler works
- the detector proposes valid import configs
- deterministic tests pass
- a local or cloud LLM adapter has been evaluated, if approved
- the simple open parser dry-run validates proposed configs
- the human can review or edit proposed configs
- docs explain the workflow and limitations
- known failure cases are documented
- the final summary is written

## Current next-step policy

When uncertain, choose the smallest safe next step.

Prefer:

```text
spec → deterministic implementation → review → docs
```

over:

```text
big implementation → many features → difficult review
```

The PoC should remain understandable, testable, original, and safe to publish.
