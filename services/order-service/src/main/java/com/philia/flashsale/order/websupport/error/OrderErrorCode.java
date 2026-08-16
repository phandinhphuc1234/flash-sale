package com.philia.flashsale.order.websupport.error;

import org.springframework.http.HttpStatus;

/** Stable Order-owned HTTP error taxonomy for the public query contract. */
public enum OrderErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found"),
    ORDER_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Order method is not allowed"),
    ORDER_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Order service error"),
    ORDER_DATABASE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Order data is temporarily unavailable");

    private final HttpStatus status;
    private final String message;

    OrderErrorCode(HttpStatus status, String message) {
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
