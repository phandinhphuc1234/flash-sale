package com.philia.flashsale.gateway.error;

import org.springframework.http.HttpStatus;

/** Gateway-owned error codes exposed through the public HTTP boundary. */
public enum GatewayErrorCode {

    INVALID_ADMIN_REQUEST(
            HttpStatus.BAD_REQUEST,
            "X-Trace-Id must be non-blank and no longer than 128 characters"),
    UNAUTHENTICATED(
            HttpStatus.UNAUTHORIZED,
            "Authentication is required"),
    CATALOG_ADMIN_REQUIRED(
            HttpStatus.FORBIDDEN,
            "CATALOG_ADMIN authority is required"),
    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "Access is denied"),
    RATE_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too many requests"),
    DOWNSTREAM_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "The requested service is temporarily unavailable"),
    AUTHENTICATION_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Authentication is temporarily unavailable"),
    GATEWAY_INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The gateway could not process the request");

    private final HttpStatus status;
    private final String message;

    GatewayErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
