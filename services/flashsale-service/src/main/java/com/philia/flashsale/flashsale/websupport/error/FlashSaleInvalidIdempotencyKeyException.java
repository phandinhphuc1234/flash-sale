package com.philia.flashsale.flashsale.websupport.error;

/** Transport validation failure for the required public idempotency header. */
public final class FlashSaleInvalidIdempotencyKeyException extends RuntimeException {
    public FlashSaleInvalidIdempotencyKeyException() {
        super("Idempotency-Key is required");
    }
}
