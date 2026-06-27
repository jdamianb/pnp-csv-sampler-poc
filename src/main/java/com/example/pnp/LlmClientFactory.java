package com.example.pnp;

/**
 * Factory for creating {@link LlmClient} instances based on {@link LlmOptions}.
 * <p>
 * If no provider is configured (null or blank), returns a {@link StubLlmClient}
 * with the given default JSON response.
 */
public class LlmClientFactory {

    private final String defaultStubJson;

    /**
     * @param defaultStubJson the JSON to return when using StubLlmClient
     */
    public LlmClientFactory(String defaultStubJson) {
        this.defaultStubJson = defaultStubJson;
    }

    /**
     * Create the appropriate LLM client based on the given options.
     *
     * @param options provider configuration
     * @return an LlmClient instance
     * @throws IllegalArgumentException if the provider is unknown
     */
    public LlmClient create(LlmOptions options) {
        if (options == null || !options.hasProvider()) {
            return new StubLlmClient(defaultStubJson);
        }

        return switch (options.provider().toLowerCase()) {
            case "ollama" -> new OllamaLlmClient(options);
            default -> throw new IllegalArgumentException(
                    "Unknown LLM provider: '" + options.provider()
                            + "'. Supported: ollama");
        };
    }

    /**
     * Convenience method to create a client with default (stub) options.
     *
     * @return a StubLlmClient with the default JSON
     */
    public LlmClient createDefault() {
        return new StubLlmClient(defaultStubJson);
    }
}
