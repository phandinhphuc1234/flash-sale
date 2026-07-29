package com.philia.flashsale.product.catalogadmin.application.exception;

public final class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("Idempotency key was reused with a different request");
    }
}
