package com.example.pnp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * LLM client for Ollama-compatible local endpoints.
 * <p>
 * Uses Java's built-in {@link HttpClient} (no additional dependencies).
 * Calls the {@code /api/generate} endpoint with {@code "stream": false}
 * and extracts the {@code "response"} field from the JSON response.
 */
public class OllamaLlmClient implements LlmClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(120);
    private static final String GENERATE_ENDPOINT = "/api/generate";

    private final String baseUrl;
    private final String model;
    private final double temperature;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * @param options LLM options with provider, baseUrl, model, temperature
     */
    public OllamaLlmClient(LlmOptions options) {
        this(options, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build());
    }

    /**
     * Package-private constructor for testing with a custom HttpClient.
     */
    OllamaLlmClient(LlmOptions options, HttpClient httpClient) {
        String url = options.baseUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Ollama base URL must not be blank");
        }
        if (options.model() == null || options.model().isBlank()) {
            throw new IllegalArgumentException("Ollama model must not be blank");
        }
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.model = options.model();
        this.temperature = options.temperature();
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String sendPrompt(String prompt) {
        try {
            return sendPromptInternal(prompt);
        } catch (IOException e) {
            throw new LlmException("Ollama request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Ollama request was interrupted", e);
        }
    }

    private String sendPromptInternal(String prompt) throws IOException, InterruptedException {
        // Build request body
        var requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);

        var optionsNode = objectMapper.createObjectNode();
        optionsNode.put("temperature", temperature);
        requestBody.set("options", optionsNode);

        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + GENERATE_ENDPOINT))
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new LlmException(
                    "Cannot connect to Ollama at " + baseUrl
                            + ". Is Ollama running? (" + e.getMessage() + ")", e);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new LlmException(
                    "Connection to Ollama at " + baseUrl + " timed out.", e);
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new LlmException(
                    "Ollama returned HTTP " + statusCode
                            + ": " + response.body());
        }

        var body = response.body();
        if (body == null || body.isBlank()) {
            throw new LlmException("Ollama returned empty response body");
        }

        // Parse the Ollama JSON response and extract the "response" field
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException e) {
            throw new LlmException(
                    "Failed to parse Ollama response as JSON: " + e.getMessage()
                            + "\nRaw response: " + body, e);
        }

        var responseField = root.get("response");
        if (responseField == null) {
            throw new LlmException(
                    "Ollama response missing 'response' field: " + body);
        }

        var result = responseField.asText();
        if (result == null || result.isBlank()) {
            throw new LlmException("Ollama returned empty 'response' field");
        }

        return result;
    }
}
