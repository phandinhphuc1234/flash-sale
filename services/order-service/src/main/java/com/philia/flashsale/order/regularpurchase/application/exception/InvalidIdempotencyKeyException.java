package com.philia.flashsale.order.regularpurchase.application.exception;

/** Public-header validation failure owned by the regular-purchase feature. */
public final class InvalidIdempotencyKeyException extends RuntimeException {
    public InvalidIdempotencyKeyException() {
        super("Idempotency key is invalid");
    }
}
