# PnP CSV Sampler — Proof of Concept

A deterministic Java 21 CLI tool that reads Pick-and-Place (PnP) CSV files as raw text and produces a structured JSON sample containing the total line count, first N lines, and last M lines — without parsing, inferring, or transforming any content.

## Quick Start

```bash
# Build
mvn clean package

# Run on example files
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/simple-pnp.csv
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/messy-pnp.csv

# Custom sample sizes
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar examples/simple-pnp.csv --first 4 --last 2

# Run tests
mvn test
```

## Usage

```
Usage: java ... com.example.pnp.Main <file> [--first N] [--last M]
  <file>        Path to PnP CSV file (required)
  --first N     Number of lines to sample from the start (default: 80)
  --last M      Number of lines to sample from the end (default: 20)

Outputs a JSON object with totalLines, firstLines, and lastLines.
```

## Example Output

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

## Project Structure

```
.
├── AGENTS.md                         # Multi-agent harness & workflow
├── .run/                             # IntelliJ run configurations (shareable)
├── docs/
│   ├── adr/                          # Architecture Decision Records
│   ├── feature-requests/
│   │   └── pnp-csv-sampler-poc.md    # Original feature request
│   ├── features/                     # Feature documentation
│   ├── specs/
│   │   └── pnp-csv-sampler-spec.md   # EARS requirements & Gherkin scenarios
│   └── testing/                      # Test strategy
├── examples/
│   ├── simple-pnp.csv                # Simple example (8 lines)
│   └── messy-pnp.csv                 # Messy example (13 lines)
├── .goose/
│   ├── handoffs/                     # Phase transition handoffs
│   ├── reviews/                      # Code review reports
│   └── roles/                        # Role definitions
├── src/
│   ├── main/java/com/example/pnp/
│   │   ├── Main.java                 # CLI entry point
│   │   ├── Sampler.java              # Core sampling logic
│   │   ├── Line.java                 # Line record (index + text)
│   │   └── SampleResult.java         # Result record (totalLines, firstLines, lastLines)
│   └── test/java/com/example/pnp/
│       ├── SamplerTest.java          # 19 unit tests
│       └── MainTest.java             # 12 CLI tests
└── pom.xml                           # Maven build (Java 21, Jackson, JUnit 5)
```

## Core Design

| Principle | Implementation |
|---|---|
| **Dumb & deterministic** | Reads raw lines, no CSV parsing, no inference, no trimming |
| **Streaming** | Ring buffer for tail — memory proportional to sample size, not file size |
| **No duplicates** | Lines overlapping between first and last samples appear only once (R10) |
| **Testable** | `Sampler` accepts `Reader` — tests don't need filesystem |
| **JSON output** | Jackson with pretty-print to stdout |

## Out of Scope

This PoC intentionally excludes: CSV parsing, delimiter/header/column inference, unit detection, line normalization, LLM calls, Spring Boot, REST API, database, and UI.

## Build Requirements

- Java 21+
- Maven 3.8+

## Multi-Agent Workflow

This project was built using a role-based Goose agent harness. See `AGENTS.md` for the full workflow definition.

The phases were:

1. **Leader** — intake feature request, hand off to spec writer
2. **Spec Writer** — EARS requirements + Gherkin scenarios
3. **Implementer** — Java 21 + Maven + Jackson + JUnit 5
4. **Reviewer** — code quality, scope compliance, test coverage
5. **Doc Writer** — documentation updates
