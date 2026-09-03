package com.philia.flashsale.cart.application.exception;

/** Safe Cart capability failure; transport/provider details never leave the outbound adapter. */
public final class ProductDisplayDependencyException extends RuntimeException {

    public enum Failure {
        TOKEN_UNAVAILABLE,
        TIMEOUT,
        CONNECTION,
        UNAUTHORIZED,
        FORBIDDEN,
        SERVER_ERROR,
        MALFORMED_RESPONSE
    }

    private final Failure failure;

    public ProductDisplayDependencyException(Failure failure) {
        super("Product display dependency is unavailable");
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }
}
