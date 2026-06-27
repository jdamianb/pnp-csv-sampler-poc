package com.example.pnp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LlmClientFactory}.
 */
class LlmClientFactoryTest {

    private static final String STUB_JSON = "{\"schemaVersion\":1}";
    private final LlmClientFactory factory = new LlmClientFactory(STUB_JSON);

    @Test
    void nullOptionsReturnsStub() {
        var client = factory.create(null);
        assertInstanceOf(StubLlmClient.class, client);
    }

    @Test
    void nullProviderReturnsStub() {
        var options = new LlmOptions(null, null, null, 0);
        var client = factory.create(options);
        assertInstanceOf(StubLlmClient.class, client);
    }

    @Test
    void blankProviderReturnsStub() {
        var options = new LlmOptions("", null, null, 0);
        var client = factory.create(options);
        assertInstanceOf(StubLlmClient.class, client);
    }

    @Test
    void ollamaProviderReturnsOllamaClient() {
        var options = new LlmOptions("ollama", "http://localhost:11434", "qwen2.5:7b", 0);
        var client = factory.create(options);
        assertInstanceOf(OllamaLlmClient.class, client);
    }

    @Test
    void ollamaProviderCaseInsensitive() {
        var options = new LlmOptions("OLLAMA", "http://localhost:11434", "model", 0);
        var client = factory.create(options);
        assertInstanceOf(OllamaLlmClient.class, client);
    }

    @Test
    void unknownProviderThrows() {
        var options = new LlmOptions("unknown", null, null, 0);
        var exception = assertThrows(IllegalArgumentException.class, () -> factory.create(options));
        assertTrue(exception.getMessage().contains("unknown"));
    }

    @Test
    void createDefaultReturnsStub() {
        var client = factory.createDefault();
        assertInstanceOf(StubLlmClient.class, client);
        var stub = (StubLlmClient) client;
        assertEquals(STUB_JSON, stub.getResponse());
    }
}
