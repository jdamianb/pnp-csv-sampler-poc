package com.example.pnp;

/**
 * Abstraction for an LLM client that accepts a prompt and returns a text response.
 * <p>
 * The first Stage 2 implementation uses {@link StubLlmClient}.
 * Real LLM adapters (Ollama, OpenAI, etc.) may be added in Stage 3.
 */
public interface LlmClient {

    /**
     * Send a prompt to the LLM and return the response.
     *
     * @param prompt the full prompt text
     * @return the LLM's response text
     */
    String sendPrompt(String prompt);
}
