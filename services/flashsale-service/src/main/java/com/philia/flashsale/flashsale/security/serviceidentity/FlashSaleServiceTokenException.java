package com.philia.flashsale.flashsale.security.serviceidentity;

/** Safe application-boundary failure when the Flash Sale machine token cannot be acquired. */
public final class FlashSaleServiceTokenException extends RuntimeException {
    public FlashSaleServiceTokenException(String message) {
        super(message);
    }

    public FlashSaleServiceTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
