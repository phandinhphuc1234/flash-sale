package com.philia.flashsale.gateway.error;

import org.springframework.http.HttpStatus;

public enum GatewayErrorCode {

    INVALID_ADMIN_REQUEST(
            HttpStatus.BAD_REQUEST,
            "X-Trace-Id must be non-blank and no longer than 128 characters"),
    UNAUTHENTICATED(
            HttpStatus.UNAUTHORIZED,
            "Authentication is required"),
    CATALOG_ADMIN_REQUIRED(
            HttpStatus.FORBIDDEN,
            "CATALOG_ADMIN authority is required");

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
