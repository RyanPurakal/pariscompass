package com.ryanpurakal.pariscompass.exception;

/**
 * Thrown when an AI projection cannot be produced: the model is not configured or the
 * upstream call failed. Mapped to 503 because the condition is temporary and the rest
 * of the API keeps working. The message is safe to show to clients; the cause is only logged.
 */
public class ProjectionUnavailableException extends RuntimeException {
    public ProjectionUnavailableException(String message) {
        super(message);
    }

    public ProjectionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
