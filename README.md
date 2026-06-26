# PnP Goose PoC Harness

This folder contains a lightweight multi-agent harness for a Java 21 Pick-and-Place CSV sampler PoC.

Roles:

1. Leader / Orchestrator
2. Spec Writer
3. Implementer
4. Reviewer
5. Doc Writer

## How to use

Copy this harness into the root of your PoC repository.

Start Goose with:

```text
Read AGENTS.md and follow the PoC workflow.
Start as the leader agent.
Use docs/feature-requests/pnp-csv-sampler-poc.md as the feature request.
Do not implement code until the spec writer produces EARS and Gherkin specs and I approve them.
```

## Human validation gates

1. Spec approval before implementation starts.
2. Implementation approval before documentation starts.
3. Documentation approval before the feature is considered complete.

## PoC scope

The first implementation should only build:

```text
CSV file path in
raw numbered sample JSON out
Java 21 Maven CLI app
JUnit tests passing
Gherkin acceptance scenarios documented, and optionally automated
```

Out of scope:

```text
Spring Boot
LLM calls
RAG
database
web UI
CSV field parsing
format detection
column mapping
fine-tuning
```
