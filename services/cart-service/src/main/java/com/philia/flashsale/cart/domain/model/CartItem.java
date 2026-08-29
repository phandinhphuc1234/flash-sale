package com.philia.flashsale.cart.domain.model;

import com.philia.flashsale.cart.domain.valueobject.CartQuantity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Cart-owned intent for a single Product variant. */
public final class CartItem {
    private final UUID variantId;
    private CartQuantity quantity;
    private final Instant createdAt;
    private Instant updatedAt;

    private CartItem(UUID variantId, CartQuantity quantity, Instant createdAt, Instant updatedAt) {
        this.variantId = Objects.requireNonNull(variantId, "variantId");
        this.quantity = Objects.requireNonNull(quantity, "quantity");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static CartItem create(UUID variantId, CartQuantity quantity, Instant now) {
        return new CartItem(variantId, quantity, now, now);
    }

    public static CartItem restore(UUID variantId, CartQuantity quantity, Instant createdAt, Instant updatedAt) {
        return new CartItem(variantId, quantity, createdAt, updatedAt);
    }

    public void replaceQuantity(CartQuantity next, Instant now) {
        this.quantity = Objects.requireNonNull(next, "quantity");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public UUID variantId() { return variantId; }
    public CartQuantity quantity() { return quantity; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
