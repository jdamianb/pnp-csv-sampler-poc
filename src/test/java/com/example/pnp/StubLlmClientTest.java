package com.example.pnp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StubLlmClient}.
 */
class StubLlmClientTest {

    @Test
    void returnsConfiguredResponse() {
        var expected = "{\"result\": \"ok\"}";
        var client = new StubLlmClient(expected);
        var response = client.sendPrompt("Any prompt here");
        assertEquals(expected, response);
    }

    @Test
    void returnsDeterministicResponse() {
        var client = new StubLlmClient("fixed");
        var first = client.sendPrompt("prompt 1");
        var second = client.sendPrompt("prompt 2");
        assertEquals(first, second);
    }

    @Test
    void getResponseReturnsConfiguredValue() {
        var client = new StubLlmClient("test value");
        assertEquals("test value", client.getResponse());
    }

    @Test
    void ignoresPromptContent() {
        var client = new StubLlmClient("always this");
        assertEquals("always this", client.sendPrompt("anything"));
        assertEquals("always this", client.sendPrompt(""));
        assertEquals("always this", client.sendPrompt(null));
    }
}
