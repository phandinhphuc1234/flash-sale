package com.philia.flashsale.common.web;

import java.time.Instant;

/**
 * Shared HTTP envelope for successful API responses.
 *
 * @param success   always {@code true}
 * @param code      stable success code, defaults to {@code SUCCESS}
 * @param message   client-facing success message
 * @param data      response payload
 * @param timestamp response creation time in UTC
 * @param <T>       response payload type
 */
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        Instant timestamp
) {

    public ApiResponse {
        success = true;
        code = code == null || code.isBlank() ? "SUCCESS" : code;
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "SUCCESS", "Operation completed successfully", data, null);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, "SUCCESS", message, data, null);
    }

    public static ApiResponse<Void> successWithoutData(String message) {
        return new ApiResponse<>(true, "SUCCESS", message, null, null);
    }
}
