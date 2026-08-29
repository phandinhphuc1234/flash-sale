package com.philia.flashsale.cart.domain.valueobject;

/** Immutable desired quantity for one cart item. */
public record CartQuantity(int value) {
    public static final int MINIMUM = 1;
    public static final int MAXIMUM = 10;

    public CartQuantity {
        if (value < MINIMUM || value > MAXIMUM) {
            throw new IllegalArgumentException("Cart quantity must be between 1 and 10");
        }
    }

    public static CartQuantity of(int value) {
        return new CartQuantity(value);
    }
}
