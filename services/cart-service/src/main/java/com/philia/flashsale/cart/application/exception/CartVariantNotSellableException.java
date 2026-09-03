package com.philia.flashsale.cart.application.exception;

/** Product-owned sellability rejection; the Cart is not mutated. */
public final class CartVariantNotSellableException extends RuntimeException {
    public CartVariantNotSellableException() {
        super("Cart variant is not sellable");
    }
}
