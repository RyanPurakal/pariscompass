package com.ryanpurakal.pariscompass.exception;

/**
 * A request that is well formed but asks for something invalid (unknown metric, bad year range,
 * wrong number of countries). Mapped to 400 with the given machine-readable code.
 */
public class InvalidRequestException extends RuntimeException {
    private final String code;

    public InvalidRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
