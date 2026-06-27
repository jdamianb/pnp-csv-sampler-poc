# PnP CSV Sampler & Format Detector — Proof of Concept

A Java 21 PoC with three stages:

1. **Stage 1 — CSV Sampler**: Deterministically reads Pick-and-Place (PnP) CSV/TSV files as raw text and produces a structured JSON sample.
2. **Stage 2 — LLM-assisted Format Detection**: Proposes a `PnpImportFormatConfig` (delimiter, columns, units, etc.) using an LLM client abstraction, validated before output.
3. **Stage 3 — Real LLM Adapter**: Adds Ollama support behind the existing pipeline. Default remains stub (offline).

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

# Run all tests (109 total, 3 skipped = integration)
mvn test

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
│       └── OllamaIntegrationTest.java # 3 tests (Stage 3, opt-in)
├── .goose/
│   ├── handoffs/                     # Phase transition handoffs
│   ├── reviews/                      # Code review reports
│   └── roles/                        # Role definitions
└── pom.xml                           # Maven build (Java 21, Jackson, JUnit 5)
```

## Tests

**109 tests total** (106 deterministic, 3 opt-in integration) — no network required for default run.

```bash
mvn test                        # All tests (stub default, 3 skipped)
mvn test -Dtest=SamplerTest     # Stage 1 sampler only
mvn test -Dtest="*Validator*"   # Stage 2 validation only
mvn test -Dtest="*Ollama*"      # Stage 3 Ollama offline tests
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
