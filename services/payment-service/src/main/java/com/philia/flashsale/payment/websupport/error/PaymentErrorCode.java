package com.philia.flashsale.payment.websupport.error;

import org.springframework.http.HttpStatus;

/** Stable public Payment error taxonomy; provider details never cross this boundary. */
public enum PaymentErrorCode {
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Payment not found"),
    PAYMENT_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "Payment idempotency key conflict"),
    PAYMENT_NOT_PAYABLE(HttpStatus.CONFLICT, "Payment is not payable"),
    PAYMENT_DEADLINE_PASSED(HttpStatus.CONFLICT, "Payment deadline has passed"),
    PAYMENT_ATTEMPT_LIMIT_REACHED(HttpStatus.CONFLICT, "Payment Checkout attempt limit reached"),
    PAYMENT_CHECKOUT_IN_PROGRESS(HttpStatus.CONFLICT, "Payment Checkout is already in progress"),
    PAYMENT_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Payment provider is temporarily unavailable"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied"),
    PAYMENT_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Payment method is not allowed"),
    PAYMENT_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Payment service error");

    private final HttpStatus status;
    private final String message;

    PaymentErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }
}
