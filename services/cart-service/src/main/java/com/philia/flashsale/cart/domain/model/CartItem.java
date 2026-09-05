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
    private long version;

    private CartItem(UUID variantId, CartQuantity quantity, Instant createdAt, Instant updatedAt, long version) {
        this.variantId = Objects.requireNonNull(variantId, "variantId");
        this.quantity = Objects.requireNonNull(quantity, "quantity");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        this.version = version;
    }

    public static CartItem create(UUID variantId, CartQuantity quantity, Instant now) {
        return create(variantId, quantity, now, 1);
    }

    public static CartItem create(UUID variantId, CartQuantity quantity, Instant now, long version) {
        return new CartItem(variantId, quantity, now, now, version);
    }

    public static CartItem restore(UUID variantId, CartQuantity quantity, Instant createdAt, Instant updatedAt) {
        return restore(variantId, quantity, createdAt, updatedAt, 1);
    }

    public static CartItem restore(UUID variantId, CartQuantity quantity, Instant createdAt, Instant updatedAt,
            long version) {
        return new CartItem(variantId, quantity, createdAt, updatedAt, version);
    }

    public void replaceQuantity(CartQuantity next, Instant now) {
        replaceQuantity(next, now, version + 1);
    }

    public void replaceQuantity(CartQuantity next, Instant now, long nextVersion) {
        this.quantity = Objects.requireNonNull(next, "quantity");
        this.updatedAt = Objects.requireNonNull(now, "now");
        if (nextVersion <= version) {
            throw new IllegalArgumentException("item version must increase");
        }
        this.version = nextVersion;
    }

    public UUID variantId() { return variantId; }
    public CartQuantity quantity() { return quantity; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
