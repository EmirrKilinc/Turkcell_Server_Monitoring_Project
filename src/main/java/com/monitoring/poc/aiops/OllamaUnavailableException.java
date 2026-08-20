package com.monitoring.poc.aiops;

/**
 * Ollama unreachable, timed out, or returned an unusable response. Mapped to
 * HTTP 502 by GlobalExceptionHandler - the frontend already falls back to a
 * locally simulated reply whenever /api/v1/aiops/chat does not return 2xx
 * (see assets/aiops.js simulateAssistantReply), so this is safe to surface
 * as-is rather than swallow.
 */
public class OllamaUnavailableException extends RuntimeException {

    public OllamaUnavailableException(String message) {
        super(message);
    }

    public OllamaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
