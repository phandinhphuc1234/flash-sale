package com.philia.flashsale.cart.adapter.out.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** JPA representation of one Cart item, keyed by (cart_id, variant_id). */
@Entity
@Table(name = "cart_items")
public class CartItemJpaEntity {
    @EmbeddedId
    private CartItemId id;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartItemJpaEntity() { }

    public CartItemJpaEntity(UUID cartId, UUID variantId, int quantity, Instant createdAt, Instant updatedAt) {
        this.id = new CartItemId(cartId, variantId);
        this.quantity = quantity;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getCartId() { return id.cartId(); }
    public UUID getVariantId() { return id.variantId(); }
    public int getQuantity() { return quantity; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Embeddable
    public static class CartItemId implements Serializable {
        @Column(name = "cart_id", nullable = false)
        private UUID cartId;
        @Column(name = "variant_id", nullable = false)
        private UUID variantId;

        protected CartItemId() { }
        public CartItemId(UUID cartId, UUID variantId) {
            this.cartId = cartId;
            this.variantId = variantId;
        }
        public UUID cartId() { return cartId; }
        public UUID variantId() { return variantId; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CartItemId that)) return false;
            return cartId.equals(that.cartId) && variantId.equals(that.variantId);
        }

        @Override
        public int hashCode() { return 31 * cartId.hashCode() + variantId.hashCode(); }
    }
}
