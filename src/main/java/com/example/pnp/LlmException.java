package com.example.pnp;

/**
 * Exception thrown when an LLM client operation fails.
 * <p>
 * This includes connection failures, non-2xx HTTP responses,
 * empty responses, and JSON parse errors.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
