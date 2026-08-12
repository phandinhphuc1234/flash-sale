package com.philia.flashsale.common.web;

import java.time.Instant;
import java.util.List;

/**
 * Shared HTTP envelope for failed API responses.
 *
 * <p>Service-specific error codes remain owned by each service; this type only standardizes the
 * transport shape.</p>
 *
 * @param success   always {@code false}
 * @param errorCode stable client-facing error code
 * @param message   client-safe error summary
 * @param errors    optional field-level validation failures
 * @param timestamp response creation time in UTC
 */
public record ApiErrorResponse(
        boolean success,
        String errorCode,
        String message,
        List<FieldViolation> errors,
        Instant timestamp
) {

    public ApiErrorResponse {
        success = false;
        errors = errors == null || errors.isEmpty() ? null : List.copyOf(errors);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public static ApiErrorResponse of(String errorCode, String message) {
        return new ApiErrorResponse(false, errorCode, message, null, null);
    }

    public static ApiErrorResponse of(String errorCode, String message, List<FieldViolation> errors) {
        return new ApiErrorResponse(false, errorCode, message, errors, null);
    }
}
