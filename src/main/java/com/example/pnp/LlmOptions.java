package com.example.pnp;

/**
 * Configuration options for LLM provider selection.
 *
 * @param provider    the LLM provider name (e.g., "ollama"), null/blank means use stub
 * @param baseUrl     base URL for the LLM API endpoint
 * @param model       model name to use
 * @param temperature temperature for generation (0 = deterministic)
 */
public record LlmOptions(
        String provider,
        String baseUrl,
        String model,
        double temperature) {

    /** Default temperature for deterministic detection. */
    public static final double DEFAULT_TEMPERATURE = 0.0;

    public LlmOptions {
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException(
                    "temperature must be between 0.0 and 2.0, got " + temperature);
        }
    }

    /** Whether a real LLM provider is configured (vs using stub). */
    public boolean hasProvider() {
        return provider != null && !provider.isBlank();
    }
}
