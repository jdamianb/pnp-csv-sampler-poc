package com.example.pnp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Offline tests for {@link OllamaLlmClient}.
 * <p>
 * These tests verify error handling without requiring a real Ollama instance.
 */
class OllamaLlmClientTest {

    @Test
    void constructorRejectsNullBaseUrl() {
        var options = new LlmOptions("ollama", null, "model", 0);
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new OllamaLlmClient(options));
        assertTrue(exception.getMessage().contains("URL"));
    }

    @Test
    void constructorRejectsBlankBaseUrl() {
        var options = new LlmOptions("ollama", " ", "model", 0);
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new OllamaLlmClient(options));
        assertTrue(exception.getMessage().contains("URL"));
    }

    @Test
    void constructorRejectsNullModel() {
        var options = new LlmOptions("ollama", "http://localhost:11434", null, 0);
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new OllamaLlmClient(options));
        assertTrue(exception.getMessage().contains("model"));
    }

    @Test
    void constructorRejectsBlankModel() {
        var options = new LlmOptions("ollama", "http://localhost:11434", " ", 0);
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new OllamaLlmClient(options));
        assertTrue(exception.getMessage().contains("model"));
    }

    @Test
    void connectionRefusedThrowsLlmException() {
        // Connect to a port that's not listening — deterministic failure
        var options = new LlmOptions("ollama", "http://localhost:18765", "test-model", 0);
        var client = new OllamaLlmClient(options);
        var exception = assertThrows(LlmException.class,
                () -> client.sendPrompt("test prompt"));
        assertTrue(exception.getMessage().contains("Cannot connect")
                || exception.getMessage().contains("Connection refused")
                || exception.getMessage().contains("refused"));
    }

    @Test
    void badUrlFormatThrowsLlmException() {
        var options = new LlmOptions("ollama", "http://invalid-url-that-does-not-exist.local", "test-model", 0);
        var client = new OllamaLlmClient(options);
        var exception = assertThrows(LlmException.class,
                () -> client.sendPrompt("test prompt"));
        // Should fail with some connection error
        assertNotNull(exception.getMessage());
    }
}
