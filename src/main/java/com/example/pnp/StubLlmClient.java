package com.example.pnp;

/**
 * Deterministic stub LLM client for testing.
 * <p>
 * Returns a fixed JSON response regardless of the prompt, making tests
 * deterministic and independent of any real LLM.
 */
public class StubLlmClient implements LlmClient {

    private final String response;

    /**
     * @param response the exact JSON string to return for any prompt
     */
    public StubLlmClient(String response) {
        this.response = response;
    }

    @Override
    public String sendPrompt(String prompt) {
        return response;
    }

    /**
     * Returns the fixed response configured for this stub.
     *
     * @return the stub response string
     */
    public String getResponse() {
        return response;
    }
}
