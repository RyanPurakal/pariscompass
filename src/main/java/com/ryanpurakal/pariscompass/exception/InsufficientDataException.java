package com.ryanpurakal.pariscompass.exception;

/** The request is valid but the country lacks the data needed to answer it. Mapped to 422. */
public class InsufficientDataException extends RuntimeException {
    public InsufficientDataException(String message) {
        super(message);
    }
}
