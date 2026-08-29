package com.philia.flashsale.cart.application.exception;

/** Product-owned variant absence; the Cart is not mutated. */
public final class CartVariantNotFoundException extends RuntimeException {
    public CartVariantNotFoundException() {
        super("Cart variant was not found");
    }
}
