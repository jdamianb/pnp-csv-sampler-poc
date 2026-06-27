# Specification: Stage 3 — Real LLM Adapter

## 1. Purpose

Add a real LLM adapter behind the existing `LlmClient` interface so that the detector can propose a `PnpImportFormatConfig` that matches the actual file content — replacing the `StubLlmClient` that currently returns a hardcoded config.

The first adapter targets **local Ollama**. The default behavior remains stubbed and deterministic.

## 2. Scope

This feature extends the existing Stage 2 pipeline with:

- `OllamaLlmClient` — a real LLM client for Ollama-compatible endpoints
- `LlmClientFactory` — creates the appropriate client based on provider configuration
- Provider configuration via CLI arguments (`--llm-provider`, `--llm-url`, `--llm-model`, `--llm-temperature`)
- Provider configuration via environment variables (`PNP_LLM_PROVIDER`, `PNP_LLM_BASE_URL`, etc.)
- Updated `detect` CLI command to wire provider config through to the factory
- Error handling for connection failures, non-JSON responses, and invalid configs
- Offline unit tests (stub remains default)
- Opt-in integration tests behind a flag (`-Dllm.integration=true`)

## 3. Out of Scope

The following are **explicitly excluded** from Stage 3:

- Parser dry-run (Stage 4)
- Repair loop (Stage 5)
- UI or human review flow (Stage 6)
- Database or persistence
- RAG or vector storage
- Fine-tuning
- Replacing the existing CSV parser
- Component-row normalization by the LLM
- OpenAI-compatible adapter (may be added later if explicitly requested)
- Automatic fallback from real LLM to stub on failure (failure must be reported)

## 4. Assumptions

- The Stage 2 pipeline exists and works (prompt builder, validator, detector, stub).
- `LlmClient` interface is sufficient and will not change.
- Ollama is installed and running on `http://localhost:11434` by default.
- The `detect` command currently uses `StubLlmClient` when no provider is configured.
- Jackson is available for JSON parsing (same as Stage 1 and 2).
- Temperature `0` is appropriate for deterministic config detection.
- The project does not have additional HTTP client dependencies yet (may need one).

## 5. EARS Requirements

### 5.1 Ubiquitous

```
R01  The system shall preserve all existing sampler, detector, and validator behavior.
R02  The system shall preserve StubLlmClient as the default when no provider is configured.
R03  The system shall not require network access for unit tests.
R04  The system shall not commit any API keys, tokens, passwords, or private URLs.
R05  The system shall pass all existing tests (Stage 1 + Stage 2) without modification.
R06  The system shall use temperature 0 for real LLM calls unless overridden by the user.
R07  The system shall support at least one real LLM provider: Ollama-compatible local endpoints.
```

### 5.2 Event-driven (When)

```
R08  When the user runs "detect <file>" without provider configuration, the system shall use StubLlmClient.
R09  When the user runs "detect <file> --llm-provider ollama", the system shall use OllamaLlmClient.
R10  When the user provides --llm-url, the system shall use that URL for the LLM endpoint.
R11  When the user provides --llm-model, the system shall use that model name for the LLM.
R12  When the user provides --llm-temperature, the system shall use that value instead of 0.
R13  When the system receives a real LLM response, it shall parse it as PnpImportFormatConfig.
R14  When the parsed config passes validation, the system shall print the config as pretty JSON.
R15  When the parsed config fails validation, the system shall print validation errors to stderr and exit with code 1.
```

### 5.3 State-driven (While)

```
R16  While sending a request to Ollama, the system shall include the prompt and temperature in the request body.
R17  While using Ollama, the system shall use the /api/generate endpoint with JSON format.
R18  While parsing a real LLM response, the system shall extract the "response" field from the Ollama JSON response.
```

### 5.4 Unwanted behavior (If)

```
R19  If the --llm-provider value is not "ollama" or not recognized, the system shall print an error and exit with code 1.
R20  If the LLM endpoint is unreachable, the system shall print a connection failure error and exit with code 1.
R21  If the LLM returns a non-2xx HTTP status, the system shall print the status and error body, then exit with code 1.
R22  If the LLM returns an empty response body, the system shall print an error and exit with code 1.
R23  If the LLM response is not valid JSON, the system shall print a parse error and exit with code 1.
R24  If the LLM response JSON is missing the expected "response" field, the system shall print an error and exit with code 1.
R25  If the LLM response cannot be parsed as PnpImportFormatConfig, the system shall the print parse error and exit with code 1.
```

## 6. Provider Configuration

### 6.1 CLI Arguments

```bash
java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar detect examples/simple-pnp.csv \
  --llm-provider ollama \
  --llm-url http://localhost:11434 \
  --llm-model qwen2.5:7b \
  --llm-temperature 0
```

### 6.2 Environment Variables

```bash
PNP_LLM_PROVIDER=ollama
PNP_LLM_BASE_URL=http://localhost:11434
PNP_LLM_MODEL=qwen2.5:7b
PNP_LLM_TEMPERATURE=0
```

### 6.3 Precedence

CLI arguments take precedence over environment variables.

If no provider is configured via either method, use `StubLlmClient`.

## 7. Gherkin Acceptance Scenarios

```gherkin
Feature: Stage 3 Real LLM Adapter

  Background:
    Given the Stage 2 detector pipeline already exists
    And StubLlmClient is the default LLM client

  Scenario: Default detect uses stub
    Given no LLM provider is configured
    When the user runs the detect command
    Then the system shall use StubLlmClient
    And the output shall be the hardcoded stub config

  Scenario: Detect with Ollama provider uses real client
    Given --llm-provider ollama is configured
    And a valid --llm-url and --llm-model are provided
    When the user runs the detect command
    Then the system shall use OllamaLlmClient
    And the system shall send a request to the Ollama endpoint

  Scenario: Unknown provider fails
    Given --llm-provider unknown is configured
    When the user runs the detect command
    Then the system shall exit with code 1
    And the error message shall indicate unknown provider

  Scenario: Missing provider URL when provider is set
    Given --llm-provider ollama is configured
    But no --llm-url or PNP_LLM_BASE_URL is set
    When the user runs the detect command
    Then the system shall exit with code 1
    And the error message shall indicate missing URL

  Scenario: Missing model when provider is set
    Given --llm-provider ollama is configured
    But no --llm-model or PNP_LLM_MODEL is set
    When the user runs the detect command
    Then the system shall exit with code 1
    And the error message shall indicate missing model

  Scenario: Ollama connection failure
    Given --llm-provider ollama is configured
    And the --llm-url points to a non-running server
    When the user runs the detect command
    Then the system shall exit with code 1
    And the error message shall indicate connection failure

  Scenario: Non-JSON response from LLM
    Given the LLM returns a non-JSON string
    When the detector parses the response
    Then the detector shall exit with code 1
    And the error message shall indicate JSON parse failure

  Scenario: Valid config from real LLM
    Given the LLM returns a valid PnpImportFormatConfig JSON
    When the detector parses and validates the response
    Then the detector shall print the config as pretty JSON
    And the exit code shall be 0

  Scenario: Invalid config from real LLM
    Given the LLM returns a valid JSON that fails validation (e.g., missing X column)
    When the detector validates the response
    Then the detector shall print validation errors to stderr
    And the exit code shall be 1

  Scenario: All existing tests pass offline
    Given no network access and no real LLM
    When mvn test is run
    Then all 77 existing tests shall pass

  Scenario: Integration tests are opt-in
    Given the standard test run
    When mvn test is executed without -Dllm.integration=true
    Then no real LLM tests shall run
```

## 8. Non-functional Requirements

| ID | Requirement |
|---|---|
| NF01 | Language: Java 21 |
| NF02 | Build tool: Maven |
| NF03 | HTTP client: Use Java `HttpClient` (built into Java 21) — no additional dependency needed |
| NF04 | JSON: Jackson 2.x (existing dependency, no new JSON library) |
| NF05 | Spring Boot: not allowed |
| NF06 | Default `detect` must use `StubLlmClient` |
| NF07 | Real LLM must be opt-in via CLI args or environment variables |
| NF08 | All unit tests must pass offline without network, API keys, or local model |
| NF09 | Integration tests gated behind `-Dllm.integration=true` |
| NF10 | Temperature defaults to `0` |
| NF11 | No API keys, tokens, or secrets committed |
| NF12 | `LlmClient` interface must not change |

## 9. Testability Notes

- `OllamaLlmClient` can be unit-tested by mocking or stubbing the HTTP layer, or by testing error paths with known bad inputs
- The factory (`LlmClientFactory`) is testable with configuration objects
- CLI argument parsing for `--llm-provider` etc. can be tested via `Main.run()` with different argument arrays
- All existing tests must continue to pass without modification
- Integration tests (real Ollama calls) are gated behind `-Dllm.integration=true`
- A simple approach: unit tests verify error handling (connection refused, non-JSON, etc.), integration tests verify actual LLM interaction

## 10. Implementation Guidance

### 10.1 OllamaLlmClient

Use Java's built-in `java.net.http.HttpClient`:

```java
var client = HttpClient.newHttpClient();
var request = HttpRequest.newBuilder()
    .uri(URI.create(baseUrl + "/api/generate"))
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(body))
    .build();
var response = client.send(request, HttpResponse.BodyHandlers.ofString());
```

Request body:

```json
{
  "model": "qwen2.5:7b",
  "prompt": "...",
  "stream": false,
  "options": {
    "temperature": 0
  }
}
```

Response parsing: extract the `"response"` field from the Ollama JSON response, then parse that as `PnpImportFormatConfig`.

### 10.2 LlmClientFactory

```java
public class LlmClientFactory {
    public static LlmClient create(LlmOptions options) {
        if (options.provider() == null) return new StubLlmClient(defaultJson);
        return switch (options.provider()) {
            case "ollama" -> new OllamaLlmClient(options);
            default -> throw new IllegalArgumentException("Unknown provider: " + options.provider());
        };
    }
}
```

### 10.3 LlmOptions

A record or class to hold provider configuration:

```java
public record LlmOptions(
    String provider,
    String baseUrl,
    String model,
    double temperature
) {}
```

### 10.4 CLI Integration

Add these flags to the `detect` command:

```
--llm-provider <name>       LLM provider (e.g., "ollama")
--llm-url <url>             LLM endpoint URL (e.g., "http://localhost:11434")
--llm-model <name>          Model name (e.g., "qwen2.5:7b")
--llm-temperature <value>   Temperature (default: 0)
```

Also support environment variables:

```
PNP_LLM_PROVIDER
PNP_LLM_BASE_URL
PNP_LLM_MODEL
PNP_LLM_TEMPERATURE
```

## 11. Human Review Checklist

- [ ] Purpose states real LLM adapter behind existing interface
- [ ] Scope limited to Ollama client with opt-in configuration
- [ ] Out of scope excludes parser dry-run, repair loop, UI, database, RAG, fine-tuning
- [ ] StubLlmClient remains default when no provider is configured
- [ ] Real LLM is opt-in via CLI args or environment variables
- [ ] No API keys or secrets are committed
- [ ] Unit tests pass offline
- [ ] Integration tests are opt-in (`-Dllm.integration=true`)
- [ ] Error handling covers connection failure, non-JSON, invalid config
- [ ] Temperature defaults to 0
- [ ] Implementation may begin after approval
