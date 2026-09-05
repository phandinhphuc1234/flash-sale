package com.philia.flashsale.order.websupport.error;

import org.springframework.http.HttpStatus;

/** Stable Order-owned HTTP error taxonomy for the public query contract. */
public enum OrderErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found"),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "Idempotency key is invalid"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency key was already used with different request content"),
    PURCHASE_RECOVERY_REQUIRED(HttpStatus.CONFLICT, "Purchase recovery is required"),
    VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product variant not found"),
    VARIANT_NOT_SELLABLE(HttpStatus.CONFLICT, "Product variant is not sellable"),
    PRICE_CHANGED(HttpStatus.CONFLICT, "One or more prices changed. Review current prices and submit a new request."),
    CART_CHANGED(HttpStatus.CONFLICT, "Cart changed while checkout was being prepared. Review the cart and submit again."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Regular stock is insufficient for the submitted purchase"),
    CHECKOUT_DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Checkout dependency is temporarily unavailable"),
    REGULAR_PURCHASE_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "Regular purchase is temporarily disabled"),
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
