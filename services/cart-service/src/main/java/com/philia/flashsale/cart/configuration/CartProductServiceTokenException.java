package com.philia.flashsale.cart.configuration;

/** Internal failure raised when Cart cannot obtain its Product machine token. */
public final class CartProductServiceTokenException extends RuntimeException {
    public CartProductServiceTokenException(String message, Throwable cause) {
        super(message, cause);
    }

    public CartProductServiceTokenException(String message) {
        super(message);
    }
}
