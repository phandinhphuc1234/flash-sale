package com.philia.flashsale.cart.security;

/** Raised when a Cart operation cannot derive a valid UUID owner from a JWT subject. */
public final class InvalidCartPrincipalException extends RuntimeException {

    public InvalidCartPrincipalException(String message) {
        super(message);
    }
}
