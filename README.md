# PnP CSV Sampler & Format Detector — Proof of Concept

A Java 21 PoC with six stages:

1. **Stage 1 — CSV Sampler**: Deterministically reads Pick-and-Place (PnP) CSV/TSV files as raw text and produces a structured JSON sample.
2. **Stage 2 — LLM-assisted Format Detection**: Proposes a `PnpImportFormatConfig` (delimiter, columns, units, etc.) using an LLM client abstraction, validated before output.
3. **Stage 3 — Real LLM Adapter**: Adds Ollama support behind the existing pipeline. Default remains stub (offline).
4. **Stage 4 — Simple Open PnP Parser**: A deterministic parser that validates whether a proposed `PnpImportFormatConfig` can actually parse the file. Produces a `PnpParseDryRunReport` with row-level errors.
5. **Stage 5 — Repair Loop**: A bounded repair loop that automatically corrects invalid `PnpImportFormatConfig` proposals by sending validation and parser errors back to the LLM.
6. **Stage 7 — Evaluation and Dataset Building**: An evaluation harness that measures the LLM-assisted format detection accuracy across 12 example files, with known failure cases documented.

## Quick Start

```bash
# Build
mvn clean package

# Stage 1: Sample example files
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/simple-pnp.csv
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/messy-pnp.csv

# Stage 2: Detect import format with stub (default, offline)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv

# Stage 3: Detect with real LLM (requires Ollama running)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv \
  --llm-provider ollama --llm-url http://localhost:11434 --llm-model qwen2.5:3b

# Custom sample sizes (works with all modes)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/simple-pnp.csv --first 4 --last 2
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv --first 40 --last 10

# Stage 4: Parse files using expected configs
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse examples-extended/03_jlcpcb_cpl_minimal.csv expected-configs/03_jlcpcb_cpl_minimal.config.json

# Run all tests (132 total, 3 skipped = integration)
mvn test

# Stage 4 parser tests only
mvn test -Dtest=SimplePnpParserTest

# Stage 5: Detect with repair loop
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv --repair-max 2

# Run repair loop on all examples with Ollama
bash scripts/run-repair-loop.sh http://localhost:11434 qwen2.5:3b

# Stage 7: Run evaluation (stub)
bash scripts/run-evaluation.sh

# Run evaluation with Ollama
bash scripts/run-evaluation.sh http://localhost:11434 qwen2.5:3b

# Generate and compare all example outputs (stub)
mvn test -Dtest=ExampleOutputGeneratorTest

# Generate with Ollama
mvn test -Dtest=ExampleOutputGeneratorTest -Dllm.integration=true -Dllm.url=http://localhost:11434 -Dllm.model=qwen2.5:3b
```

## Stage 1 — CSV Sampler

### Usage

```
java ... com.example.pnp.Main sample <file> [--first N] [--last M]
```

### Example Output

```json
{
  "totalLines" : 8,
  "firstLines" : [ {
    "index" : 0,
    "text" : "# Pick and Place Export"
  }, {
    "index" : 1,
    "text" : "# Source: Example CAD Tool"
  } ],
  "lastLines" : [ ]
}
```

### Design

| Principle | Implementation |
|---|---|
| **Dumb & deterministic** | Reads raw lines, no CSV parsing, no inference, no trimming |
| **Streaming** | Ring buffer for tail — memory proportional to sample size, not file size |
| **No duplicates** | Lines overlapping between first and last samples appear only once (R10) |
| **Testable** | `Sampler` accepts `Reader` — tests don't need filesystem |

## Stage 2 — LLM-assisted Format Detection

### CLI Usage

```bash
# Detect import format config
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv
```

### Pipeline

```
detect <file> [--llm-provider ...]
  ├── Sampler (Stage 1) → SampleResult
  ├── FormatDetectionPromptBuilder → prompt
  ├── LlmClientFactory
  │     ├── provider=null/blank → StubLlmClient (default)
  │     └── provider="ollama"   → OllamaLlmClient
  ├── PnpImportFormatConfigValidator → validation
  └── Output: config JSON or validation/LLM errors
```

### Components

| Component | Purpose |
|---|---|
| `PnpImportFormatConfig` | Config model: 14 fields |
| `ColumnMapping` / `ColumnSource` | Column mapping: `HEADER_NAME` or `COLUMN_INDEX` |
| `FormatDetectionPromptBuilder` | Builds LLM prompt from sample |
| `LlmClient` interface | Abstraction for LLM calls |
| `StubLlmClient` | Deterministic stub (default) |
| `OllamaLlmClient` | Real LLM client using Java `HttpClient` |
| `LlmClientFactory` | Factory: stub by default, Ollama when configured |
| `LlmOptions` | Provider config: provider, baseUrl, model, temperature |
| `PnpImportFormatConfigValidator` | 12 validation rules |
| `Detector` | Orchestrator: prompt → LLM → parse → validate |
| `Main` (updated) | CLI: `sample`, `detect`, `--llm-*` flags |

### Validation Rules (12 rules)

Rejects configs missing X, Y, angle, or identity columns; blank delimiter; invalid confidence/row indexes; same column index for x/y/angle; non-numeric COLUMN_INDEX values.

## Stage 3 — Real LLM Adapter (Ollama)

### CLI Usage

```bash
# Default: stub (offline, no LLM required)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv

# With Ollama (real LLM analysis)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv \
  --llm-provider ollama \
  --llm-url http://localhost:11434 \
  --llm-model qwen2.5:3b

# Or via environment variables
PNP_LLM_PROVIDER=ollama PNP_LLM_BASE_URL=http://localhost:11434 \
PNP_LLM_MODEL=qwen2.5:3b \
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv
```

### Provider Configuration

| Flag | Env Variable | Default | Description |
|---|---|---|---|
| `--llm-provider NAME` | `PNP_LLM_PROVIDER` | (stub) | Provider: "ollama" |
| `--llm-url URL` | `PNP_LLM_BASE_URL` | — | Ollama endpoint URL |
| `--llm-model MODEL` | `PNP_LLM_MODEL` | — | Model name (e.g., `qwen2.5:3b`) |
| `--llm-temperature T` | `PNP_LLM_TEMPERATURE` | 0 | Temperature (0 = deterministic) |

CLI args take precedence over environment variables.

### Error Handling

| Scenario | Behavior |
|---|---|
| No provider configured | StubLlmClient, exit 0 |
| Unknown provider | Error message, exit 1 |
| Missing URL/model | Error from constructor, exit 1 |
| Ollama not running | Connection refused error, exit 1 |
| Non-JSON response | Parse error, exit 1 |
| Invalid config | Validation errors, exit 1 |

## Stage 4 — Simple Open PnP Parser Dry-run

### CLI Usage

```bash
# Parse a file using an expected config
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar parse <file> <config-json>
```

The parser reads the file and config, validates the config, parses data rows, and outputs a `PnpParseDryRunReport`.

### Pipeline

```
parse <file> <config-json>
  ├── Read config JSON → PnpImportFormatConfig
  ├── PnpImportFormatConfigValidator → validate config
  ├── SimplePnpParser.parse(reader, config)
  │     ├── Read all lines
  │     ├── Resolve HEADER_NAME / COLUMN_INDEX columns
  │     ├── Apply linesToIgnore, dataStartRowIndex, dataEndRowIndex
  │     ├── Split rows on delimiter (",", ";", "\t", WHITESPACE)
  │     ├── Parse X/Y/angle with decimal separator handling
  │     ├── Strip unit suffixes (mm, deg, etc.)
  │     ├── Apply side value mappings
  │     └── Collect row-level errors
  └── Output: PnpParseDryRunReport JSON
```

### Supported Formats

| Delimiter | Config Value | Example |
|---|---|---|
| Comma | `,` | JLCPCB, Altium, EasyEDA |
| Semicolon | `;` | European decimal comma |
| Tab | `\t` or `\\t` | Machine TSV |
| Whitespace | `WHITESPACE` | KiCad `.pos` |

## Stage 5 — Repair Loop for Config Correction

### CLI Usage

```bash
# Detect with repair enabled (default: max 2 attempts)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv

# Detect without repair (original behavior)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv --no-repair

# Run repair loop on all examples with Ollama
bash scripts/run-repair-loop.sh
```

### Pipeline

```
detect <file> [--repair-max N]
  │
  ├── Detector.detect(sample) → DetectionResult
  ├── If valid AND parser succeeds → output (no repair)
  └── Else (repair loop, up to maxRetries):
        ├── Build repair prompt (sample + broken config + errors)
        ├── Send to LLM → corrected config
        ├── Validate + parser-check
        ├── If valid → output with repaired:true
        ├── If identical JSON → stop (stub detected)
        └── Exhausted → expose errors
```

## Stage 7 — Evaluation and Dataset Building

An evaluation harness that measures the LLM-assisted format detection pipeline accuracy against 12 example files with known expected configurations.

### Results (Ollama, qwen2.5:3b)

| Metric | Rate |
|---|---|
| Valid JSON | 91.7% |
| Validator Pass | 91.7% |
| Parser Success | 66.7% |
| Config Accuracy | 66.7% |
| Repair Effectiveness | 40.0% |

**Key finding**: Comma-delimited files achieve 100% accuracy. The model struggles with non-comma delimiters (whitespace, semicolon, tab).

### Script: run-evaluation.sh

The `scripts/run-evaluation.sh` script runs the full evaluation pipeline on all 12 files in `examples-extended/` with stub or Ollama.

```bash
# Run with StubLlmClient (default)
bash scripts/run-evaluation.sh

# Run with Ollama
bash scripts/run-evaluation.sh http://localhost:11434 qwen2.5:3b
```

Outputs:
- `target/evaluation-report.json` — machine-readable metrics
- `target/evaluation-report.md` — human-readable report with known failure cases
- `target/eval-outputs/` — per-file detailed results

### IntelliJ Configurations

| Configuration | LLM |
|---|---|
| `Run evaluation (stub)` | StubLlmClient (default) |
| `Run evaluation (Ollama)` | Ollama qwen2.5:3b |

## Project Structure

```
.
├── AGENTS.md                         # Multi-agent harness & workflow
├── .run/                             # IntelliJ run configurations (shareable)
├── docs/
│   ├── adr/                          # Architecture Decision Records
│   ├── feature-requests/             # Feature requests
│   ├── features/                     # Feature documentation
│   ├── specs/                        # EARS requirements & Gherkin scenarios
│   └── testing/                      # Test strategy
├── examples/
│   ├── simple-pnp.csv                # Simple example (8 lines)
│   └── messy-pnp.csv                 # Messy example (13 lines)
├── examples-extended/                # 12 diverse PnP file examples
├── expected-configs/                 # Expected config JSONs for extended examples
├── src/
│   ├── main/java/com/example/pnp/
│   │   ├── Main.java                 # CLI entry point (sample + detect)
│   │   ├── Sampler.java              # Core sampling logic (Stage 1)
│   │   ├── Line.java                 # Line record
│   │   ├── SampleResult.java         # Sample result record
│   │   ├── ColumnSource.java         # HEADER_NAME / COLUMN_INDEX enum (Stage 2)
│   │   ├── ColumnMapping.java        # Column mapping record (Stage 2)
│   │   ├── PnpImportFormatConfig.java# Config model (Stage 2)
│   │   ├── LlmClient.java            # LLM interface (Stage 2)
│   │   ├── StubLlmClient.java        # Deterministic stub (Stage 2)
│   │   ├── OllamaLlmClient.java      # Ollama client via Java HttpClient (Stage 3)
│   │   ├── LlmClientFactory.java     # Factory: stub/ollama (Stage 3)
│   │   ├── LlmOptions.java           # Provider config record (Stage 3)
│   │   ├── LlmException.java         # LLM error exception (Stage 3)
│   │   ├── PnpPlacement.java         # Normalized placement DTO (Stage 4)
│   │   ├── PnpParseDryRunReport.java # Dry-run report DTO (Stage 4)
│   │   ├── SimplePnpParser.java      # Deterministic parser (Stage 4)
│   │   ├── RepairPromptBuilder.java  # Repair prompt builder (Stage 5)
│   │   ├── RepairLoop.java           # Bounded repair orchestrator (Stage 5)
│   │   ├── FormatDetectionPromptBuilder.java # Prompt builder (Stage 2)
│   │   ├── PnpImportFormatConfigValidator.java # Validator (Stage 2)
│   │   └── Detector.java             # Orchestrator (Stage 2)
│   └── test/java/com/example/pnp/
│       ├── SamplerTest.java          # 19 tests
│       ├── MainTest.java             # 25 CLI tests (sample + detect + --llm-*)
│       ├── FormatDetectionPromptBuilderTest.java  # 8 tests
│       ├── PnpImportFormatConfigValidatorTest.java# 21 tests
│       ├── StubLlmClientTest.java    # 4 tests
│       ├── PnpImportFormatConfigSerializationTest.java # 3 tests
│       ├── DetectorTest.java         # 4 tests
│       ├── ExampleOutputGeneratorTest.java # 1 (generates + compares all examples)
│       ├── LlmOptionsTest.java       # 8 tests (Stage 3)
│       ├── LlmClientFactoryTest.java # 7 tests (Stage 3)
│       ├── OllamaLlmClientTest.java  # 6 tests (Stage 3, offline)
│       ├── OllamaIntegrationTest.java # 3 tests (Stage 3, opt-in)
│       ├── SimplePnpParserTest.java   # 23 tests (Stage 4)
│       ├── RepairPromptBuilderTest.java # 7 tests (Stage 5)
│       ├── RepairLoopTest.java         # 6 tests (Stage 5)
│       ├── RepairLoopExampleTest.java  # Diagnostic tool (Stage 5)
│       └── scripts/
│           ├── run-repair-loop.sh      # Repair loop runner (Stage 5)
│           └── run-evaluation.sh       # Evaluation harness (Stage 7)
├── .goose/
│   ├── handoffs/                     # Phase transition handoffs
│   ├── reviews/                      # Code review reports
│   └── roles/                        # Role definitions
└── pom.xml                           # Maven build (Java 21, Jackson, JUnit 5)
```

## Tests

**150 tests total** (147 deterministic, 3 opt-in integration) — no network required for default run. Plus evaluation harness at `scripts/run-evaluation.sh`.

```bash
mvn test                        # All tests (stub default, 3 skipped)
mvn test -Dtest=SamplerTest     # Stage 1 sampler only
mvn test -Dtest="*Validator*"   # Stage 2 validation only
mvn test -Dtest="*Ollama*"      # Stage 3 Ollama offline tests
mvn test -Dtest=SimplePnpParserTest  # Stage 4 parser tests
mvn test -Dtest="*Parser*"      # All Stage 4 parser tests
mvn test -Dtest=RepairPromptBuilderTest,RepairLoopTest  # Stage 5 repair tests
mvn test -Dtest=RepairLoopExampleTest  # Stage 5 repair diagnostic (stub)
mvn test -Dtest=RepairLoopExampleTest -Dllm.integration=true  # With Ollama
mvn test -Dllm.integration=true # Include Ollama integration tests (requires Ollama)
mvn test -Dtest=ExampleOutputGeneratorTest  # Generate & compare (stub)
mvn test -Dtest=ExampleOutputGeneratorTest -Dllm.integration=true \
  -Dllm.url=http://localhost:11434 -Dllm.model=qwen2.5:3b  # With Ollama
```

## Build Requirements

- Java 21+
- Maven 3.8+

## Multi-Agent Workflow

This project was built using a role-based Goose agent harness. See `AGENTS.md` for the full workflow definition.

### Stage 1 Phases
1. **Leader** — intake feature request, hand off to spec writer
2. **Spec Writer** — EARS requirements + Gherkin scenarios
3. **Implementer** — Java 21 + Maven + Jackson + JUnit 5
4. **Reviewer** — code quality, scope compliance, test coverage
5. **Doc Writer** — documentation updates

### Stage 2 Phases
1. **Leader** — intake Stage 2 feature request
2. **Spec Writer** — EARS + Gherkin for format detection
3. **Implementer** — config model, prompt builder, validator, stub LLM, detect CLI
4. **Reviewer** — scope compliance, validation rules, prompt quality
5. **Doc Writer** — feature/testing/ADR docs, README update

### Stage 3 Phases
1. **Leader** — intake Stage 3 feature request
2. **Spec Writer** — EARS + Gherkin for real LLM adapter
3. **Implementer** — Ollama client, factory, CLI flags, error handling
4. **Reviewer** — offline tests, stub default, no secrets, error handling
5. **Doc Writer** — feature/testing docs, README update

### Stage 4 Phases
1. **Leader** — intake Stage 4 feature request (redefined: original parser, not proprietary)
2. **Spec Writer** — EARS + Gherkin for simple deterministic parser
3. **Implementer** — PnpPlacement, PnpParseDryRunReport, SimplePnpParser, parse CLI
4. **Reviewer** — no proprietary code, all delimiter types, row-level errors
5. **Doc Writer** — feature/testing docs, ADR update, README update

### Stage 5 Phases
1. **Leader** — intake Stage 5 feature request (bounded repair loop)
2. **Spec Writer** — EARS + Gherkin for repair loop
3. **Implementer** — RepairPromptBuilder, RepairLoop, --repair-max/--no-repair CLI
4. **Reviewer** — stub detection, bounded retries, parser verification
5. **Doc Writer** — feature/testing docs, README update

### Stage 7 Phases
1. **Leader** — intake Stage 7 feature request (evaluation and dataset building)
2. **Spec Writer** — EARS + Gherkin for evaluation metrics and report format
3. **Implementer** — `scripts/run-evaluation.sh`, per-file results, known failure cases
4. **Reviewer** — metrics accuracy, report completeness, no regressions
5. **Doc Writer** — feature/testing docs, README, AGENTS.md update
