package com.philia.flashsale.cart.application.result;

import com.philia.flashsale.cart.domain.valueobject.CartQuantity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence-neutral saved Cart intent returned by the Cart adapter. */
public record CartItemState(UUID variantId, CartQuantity quantity, long itemVersion, Instant updatedAt) {
    public CartItemState(UUID variantId, CartQuantity quantity, Instant updatedAt) {
        this(variantId, quantity, 1, updatedAt);
    }

    public CartItemState {
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (itemVersion <= 0) {
            throw new IllegalArgumentException("itemVersion must be positive");
        }
    }
}
