# PnP CSV Sampler & LLM-assisted Import Format PoC

A Java 21 proof of concept for reducing the manual setup required to import Pick-and-Place (PnP) CSV/TSV-like files.

The project explores this workflow:

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
Repair loop, when needed
  ↓
Evaluation report
```

The LLM is used as a **configuration assistant**. It proposes how the file should be read. It does **not** replace the deterministic parser and it does **not** parse placement rows directly.

## Why this exists

PnP files exported from CAD tools and assembly machines are often similar, but not identical.

A user may need to manually configure:

- which lines to ignore
- which row contains the header
- where data starts and ends
- which column is reference/designator
- which column is part number
- which column is JEDEC/package/footprint
- which column is X
- which column is Y
- which column is angle/rotation
- which column is side/layer
- which delimiter is used
- which decimal separator is used
- which units are used

This PoC tries to reduce that manual work.

## Important boundaries

This is a public, original PoC.

It does not use proprietary company parser code, proprietary examples, customer files, or internal machine specifications.

The simple parser in this repository is not intended to be production-grade. It exists to validate whether an LLM-proposed import configuration is plausible and parseable.

## Current PoC stages

### Stage 1 — CSV sampler

The sampler reads a PnP CSV/TSV-like file as raw text and produces a numbered sample.

The sampler is intentionally dumb and deterministic:

- no CSV parsing
- no delimiter inference
- no column mapping
- no trimming
- no LLM calls

Example:

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar sample examples/simple-pnp.csv
```

### Stage 2 — LLM-assisted format detection contract

Stage 2 introduced the configuration model and detector boundary:

- `PnpImportFormatConfig`
- `ColumnMapping`
- `ColumnSource`
- `FormatDetectionPromptBuilder`
- `PnpImportFormatConfigValidator`
- `LlmClient`
- `StubLlmClient`
- `Detector`

The detector proposes parser configuration JSON, not normalized placement rows.

Example:

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv
```

By default, detection should remain deterministic and testable without a network connection.

### Stage 3 — Real LLM adapter

Stage 3 adds a real LLM provider behind the `LlmClient` abstraction.

The intended local-first provider is Ollama.

Example with Ollama:

```bash
export PNP_LLM_PROVIDER=ollama
export PNP_LLM_BASE_URL=http://localhost:11434
export PNP_LLM_MODEL=qwen2.5:3b
export PNP_LLM_TEMPERATURE=0

java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv
```

Unit tests must still pass without Ollama or internet access.

### Stage 4 — Simple open PoC parser dry-run

Stage 4 adds a simple original parser for this public PoC.

It consumes:

```text
PnP file + PnpImportFormatConfig
```

and produces a dry-run report such as:

```text
PnpParseDryRunReport
```

The parser validates whether the proposed config can actually parse rows into a small normalized placement preview.

Example:

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse examples/simple-pnp.csv expected-configs/simple-pnp.config.json
```

Supported concepts include:

- header-name mappings
- column-index mappings
- comma, semicolon, tab, and simple whitespace delimiters
- decimal separator `.` and `,`
- simple unit suffix cleanup such as `mm`
- side/layer value mappings
- row-level parse errors

### Stage 5 — Repair loop

Stage 5 uses validation or parser dry-run errors to ask the LLM for a corrected configuration.

The repair loop must be bounded. It should stop after a small number of attempts and expose a clear failure if no valid config can be produced.

Example flow:

```text
LLM proposed config
  ↓
validator/simple parser dry-run error
  ↓
repair prompt
  ↓
corrected config
```

### Stage 6 — Import configuration review and correction artifact

Stage 6 is a product PoC stage, not a Goose harness approval gate.

The goal is to produce a reviewable artifact showing:

- detected config
- repaired config, if any
- parser dry-run result
- errors and warnings
- suggested manual corrections

This stage helps a user accept, edit, or reject the proposed import configuration.

### Stage 7 — Evaluation and dataset building

Stage 7 evaluates the PoC over a set of synthetic PnP examples.

Useful metrics include:

- valid JSON rate
- validator pass rate
- parser success rate
- config accuracy
- repair effectiveness
- delimiter accuracy
- header row accuracy
- data start row accuracy
- decimal separator accuracy

Example report outputs may be generated under:

```text
target/eval-outputs/
target/evaluation-report.json
```

### Stage 8 — Fine-tuning investigation

Fine-tuning is intentionally future work.

It should only be considered after enough labeled examples exist:

```text
CsvSample JSON → approved PnpImportFormatConfig JSON
```

Before fine-tuning, the project should evaluate whether deterministic pre-analysis and prompt improvements solve the major failure cases.

## Separator detection improvement

Stage 7 evaluation showed that failures were concentrated around non-comma delimiters. A deterministic `SeparatorCandidateAnalyzer` was implemented to pre-analyze sampled lines for delimiter candidates before calling the LLM.

The analysis computes for each candidate delimiter (`,`, `;`, `\t`, `WHITESPACE`):
- column count consistency
- header keyword matching (Ref, X, Y, Angle, Side)
- confidence scores
- recommended delimiter and decimal separator

The results are injected into both the detection prompt and the repair prompt. The analyzer correctly identifies all 4 delimiter types when tested directly, though `qwen2.5:3b` does not consistently follow the hints for non-comma delimiters. A larger model would likely benefit from this pre-analysis.

### Files

- `src/main/java/com/example/pnp/SeparatorCandidateAnalyzer.java` — deterministic analyzer (408 lines)
- `FormatDetectionPromptBuilder.java` — prompt now includes separator analysis
- `RepairPromptBuilder.java` — repair prompt includes analysis on delimiter errors
- `src/test/java/com/example/pnp/SeparatorCandidateAnalyzerTest.java` — 11 new tests

### Test count

**161 tests total** (158 deterministic, 3 opt-in integration) — no network required for default run.

## Build requirements

- Java 21+
- Maven 3.8+
- Optional: Ollama for local LLM tests

## Build

```bash
mvn clean package
```

## Test

All default tests should be deterministic and offline:

```bash
mvn test
```

Real LLM integration tests should be opt-in only.

Example, depending on the implementation:

```bash
mvn test -Dllm.integration=true
```

## Common commands

Sample a file:

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar sample examples/simple-pnp.csv
```

Detect an import configuration:

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv
```

Parse with an expected or proposed config:

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse examples/simple-pnp.csv expected-configs/simple-pnp.config.json
```

Run evaluation, depending on the current CLI/test harness:

```bash
mvn test -Dtest=*Evaluation*
```

or use the project-specific evaluation command documented under `docs/testing/`.

## Example config shape

```json
{
  "schemaVersion": 1,
  "confidence": 0.91,
  "delimiter": ";",
  "quoteChar": "\"",
  "encoding": "UTF-8",
  "linesToIgnore": [0, 1, 2, 3, 4, 10, 11],
  "headerRowIndex": 5,
  "dataStartRowIndex": 6,
  "dataEndRowIndex": 9,
  "decimalSeparator": ",",
  "columns": {
    "reference": {
      "source": "HEADER_NAME",
      "value": "RefDes"
    },
    "partNumber": {
      "source": "HEADER_NAME",
      "value": "PartNo"
    },
    "jedec": {
      "source": "HEADER_NAME",
      "value": "Package"
    },
    "x": {
      "source": "HEADER_NAME",
      "value": "X-Pos"
    },
    "y": {
      "source": "HEADER_NAME",
      "value": "Y-Pos"
    },
    "angle": {
      "source": "HEADER_NAME",
      "value": "Angle"
    },
    "side": {
      "source": "HEADER_NAME",
      "value": "MountSide"
    }
  },
  "units": {
    "x": "mm",
    "y": "mm",
    "angle": "deg"
  },
  "valueMappings": {
    "side": {
      "T": "Top",
      "B": "Bottom"
    }
  },
  "warnings": []
}
```

## Repository structure

The repository may contain:

```text
.
├── AGENTS.md
├── README.md
├── LICENSE
├── pom.xml
├── docs/
│   ├── adr/
│   ├── feature-requests/
│   ├── features/
│   ├── specs/
│   └── testing/
├── examples/
├── examples-extended/
├── expected-configs/
├── src/
│   ├── main/java/com/example/pnp/
│   └── test/java/com/example/pnp/
└── target/
```

## Multi-agent workflow

This project is developed using a role-based Goose agent harness.

See:

```text
AGENTS.md
.goose/roles/
.goose/workflows/
```

Typical workflow:

```text
Leader
  ↓
Spec Writer
  ↓
Human spec approval
  ↓
Implementer
  ↓
Reviewer
  ↓
Human implementation approval
  ↓
Doc Writer
  ↓
Human final approval
```

## Data safety

Do not commit proprietary or customer PnP files.

Use synthetic examples unless the file is explicitly approved for public use.

Do not copy proprietary parser behavior or internal company documentation into this repository.

## License

This project is released under the MIT License.

See [LICENSE](LICENSE).
