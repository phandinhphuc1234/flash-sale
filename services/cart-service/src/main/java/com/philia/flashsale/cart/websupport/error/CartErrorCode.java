package com.philia.flashsale.cart.websupport.error;

import org.springframework.http.HttpStatus;

/** Stable public error vocabulary for Cart mutation endpoints. */
public enum CartErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    CART_VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Cart request is invalid"),
    CART_VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "Cart variant was not found"),
    CART_VARIANT_NOT_SELLABLE(HttpStatus.CONFLICT, "Cart variant is not sellable"),
    CART_PRODUCT_DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "Product details are temporarily unavailable"),
    CART_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Cart operation failed");

    private final HttpStatus status;
    private final String message;

    CartErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }
}
