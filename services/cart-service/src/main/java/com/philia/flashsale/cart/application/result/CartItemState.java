package com.philia.flashsale.cart.application.result;

import com.philia.flashsale.cart.domain.valueobject.CartQuantity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence-neutral saved Cart intent returned by the Cart adapter. */
public record CartItemState(UUID variantId, CartQuantity quantity, Instant updatedAt) {
    public CartItemState {
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
