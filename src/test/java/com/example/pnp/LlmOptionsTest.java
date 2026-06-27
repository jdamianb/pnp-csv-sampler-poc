package com.example.pnp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LlmOptions}.
 */
class LlmOptionsTest {

    @Test
    void defaultTemperatureIsZero() {
        assertEquals(0.0, LlmOptions.DEFAULT_TEMPERATURE);
    }

    @Test
    void nullProviderHasProviderReturnsFalse() {
        var opts = new LlmOptions(null, null, null, 0);
        assertFalse(opts.hasProvider());
    }

    @Test
    void blankProviderHasProviderReturnsFalse() {
        var opts = new LlmOptions("", null, null, 0);
        assertFalse(opts.hasProvider());
    }

    @Test
    void validProviderHasProviderReturnsTrue() {
        var opts = new LlmOptions("ollama", "url", "model", 0);
        assertTrue(opts.hasProvider());
    }

    @Test
    void constructorRejectsNegativeTemperature() {
        assertThrows(IllegalArgumentException.class,
                () -> new LlmOptions("ollama", "url", "model", -1.0));
    }

    @Test
    void constructorRejectsTooHighTemperature() {
        assertThrows(IllegalArgumentException.class,
                () -> new LlmOptions("ollama", "url", "model", 2.5));
    }

    @Test
    void constructorAcceptsValidTemperature() {
        var opts = new LlmOptions("ollama", "url", "model", 0.5);
        assertEquals(0.5, opts.temperature());
    }

    @Test
    void recordComponents() {
        var opts = new LlmOptions("ollama", "http://localhost:11434", "qwen2.5:7b", 0);
        assertEquals("ollama", opts.provider());
        assertEquals("http://localhost:11434", opts.baseUrl());
        assertEquals("qwen2.5:7b", opts.model());
        assertEquals(0.0, opts.temperature());
    }
}
