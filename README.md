# PnP CSV Sampler & Format Detector — Proof of Concept

A Java 21 PoC with two stages:

1. **Stage 1 — CSV Sampler**: Deterministically reads Pick-and-Place (PnP) CSV/TSV files as raw text and produces a structured JSON sample.
2. **Stage 2 — LLM-assisted Format Detection**: Proposes a `PnpImportFormatConfig` (delimiter, columns, units, etc.) using an LLM client abstraction, validated before output.

## Quick Start

```bash
# Build
mvn clean package

# Stage 1: Sample example files
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/simple-pnp.csv
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/messy-pnp.csv

# Stage 2: Detect import format (uses StubLlmClient in Stage 2)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv

# Custom sample sizes (works with both sample and detect)
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/simple-pnp.csv --first 4 --last 2
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv --first 40 --last 10

# Run all tests (Stage 1 + Stage 2)
mvn test

# Generate and compare all example outputs
mvn test -Dtest=ExampleOutputGeneratorTest
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
detect <file>
  ├── Sampler (Stage 1) → SampleResult
  ├── FormatDetectionPromptBuilder → prompt
  ├── LlmClient → JSON response
  │     └── StubLlmClient (current) → Real LLM (Stage 3)
  ├── PnpImportFormatConfigValidator → validation
  └── Output: config JSON or validation errors
```

### Components

| Component | Purpose |
|---|---|
| `PnpImportFormatConfig` | Config model: 14 fields |
| `ColumnMapping` / `ColumnSource` | Column mapping: `HEADER_NAME` or `COLUMN_INDEX` |
| `FormatDetectionPromptBuilder` | Builds LLM prompt from sample |
| `LlmClient` interface | Abstraction for LLM calls |
| `StubLlmClient` | Deterministic stub for testing |
| `PnpImportFormatConfigValidator` | 12 validation rules |
| `Detector` | Orchestrator: prompt → LLM → parse → validate |
| `Main` (updated) | CLI: `sample <file>` and `detect <file>` |

### Validation Rules (12 rules)

Rejects configs missing X, Y, angle, or identity columns; blank delimiter; invalid confidence/row indexes; same column index for x/y/angle; non-numeric COLUMN_INDEX values.

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
│   │   ├── FormatDetectionPromptBuilder.java # Prompt builder (Stage 2)
│   │   ├── PnpImportFormatConfigValidator.java # Validator (Stage 2)
│   │   └── Detector.java             # Orchestrator (Stage 2)
│   └── test/java/com/example/pnp/
│       ├── SamplerTest.java          # 19 tests
│       ├── MainTest.java             # 22 CLI tests
│       ├── FormatDetectionPromptBuilderTest.java  # 8 tests
│       ├── PnpImportFormatConfigValidatorTest.java# 21 tests
│       ├── StubLlmClientTest.java    # 4 tests
│       ├── PnpImportFormatConfigSerializationTest.java # 3 tests
│       ├── DetectorTest.java         # 4 tests
│       └── ExampleOutputGeneratorTest.java # 1 integration test
├── .goose/
│   ├── handoffs/                     # Phase transition handoffs
│   ├── reviews/                      # Code review reports
│   └── roles/                        # Role definitions
└── pom.xml                           # Maven build (Java 21, Jackson, JUnit 5)
```

## Tests

**77 tests total** — all deterministic, no network required.

```bash
mvn test                        # All tests
mvn test -Dtest=SamplerTest     # Stage 1 sampler only
mvn test -Dtest="*Validator*"   # Stage 2 validation only
mvn test -Dtest=ExampleOutputGeneratorTest  # Generate & compare example outputs
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
