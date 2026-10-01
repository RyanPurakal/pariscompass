package com.ryanpurakal.pariscompass.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Turns every error into an RFC 9457 problem+json body:
 * {@code {type, title, status, detail, instance, code, timestamp}}.
 *
 * Extending ResponseEntityExceptionHandler means Spring MVC's own errors (unknown route,
 * wrong method, failed validation, malformed body) use the same shape as ours.
 * {@code code} is a stable machine-readable string so clients never parse {@code detail}.
 * Unexpected exceptions return a generic 500 and are logged with their stack trace;
 * internal messages are never sent to the client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CountryNotFoundException.class)
    public ProblemDetail handleCountryNotFound(CountryNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "COUNTRY_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InsufficientDataException.class)
    public ProblemDetail handleInsufficientData(InsufficientDataException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_DATA", ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    /** Lists each failed constraint instead of Spring's generic "Validation failure". */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName() + " " + error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        ex.getBody().setDetail(detail);
        return handleExceptionInternal(ex, null, headers, status, request);
    }

    /**
     * Final hook for Spring MVC's own exceptions. Their problem body is created after
     * handleExceptionInternal runs, so code and timestamp are added here, where the body is complete.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail pd) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            pd.setProperty("code", status != null ? status.name() : "HTTP_" + statusCode.value());
            pd.setProperty("timestamp", Instant.now());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
