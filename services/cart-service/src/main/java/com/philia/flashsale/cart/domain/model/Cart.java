package com.philia.flashsale.cart.domain.model;

import com.philia.flashsale.cart.domain.valueobject.CartQuantity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Aggregate protecting one authenticated shopper's durable cart intent. */
public final class Cart {
    private final UUID id;
    private final UUID ownerId;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;
    private final Map<UUID, CartItem> items = new LinkedHashMap<>();

    private Cart(UUID id, UUID ownerId, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        this.version = version;
    }

    public static Cart create(UUID id, UUID ownerId, Instant now) {
        return new Cart(id, ownerId, now, now, 0);
    }

    public static Cart restore(UUID id, UUID ownerId, Instant createdAt, Instant updatedAt,
            List<CartItem> restoredItems) {
        return restore(id, ownerId, createdAt, updatedAt, 0, restoredItems);
    }

    public static Cart restore(UUID id, UUID ownerId, Instant createdAt, Instant updatedAt,
            long version, List<CartItem> restoredItems) {
        Cart cart = new Cart(id, ownerId, createdAt, updatedAt, version);
        if (restoredItems != null) {
            restoredItems.forEach(item -> cart.items.put(item.variantId(), item));
        }
        return cart;
    }

    public CartItem setItem(UUID variantId, CartQuantity quantity, Instant now) {
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(now, "now");
        long nextVersion = version + 1;
        CartItem item = items.get(variantId);
        if (item == null) {
            item = CartItem.create(variantId, quantity, now, nextVersion);
            items.put(variantId, item);
        } else {
            item.replaceQuantity(quantity, now, nextVersion);
        }
        version = nextVersion;
        updatedAt = now;
        return item;
    }

    public void removeItem(UUID variantId, Instant now) {
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(now, "now");
        if (items.remove(variantId) != null) {
            version++;
            updatedAt = now;
        }
    }

    public List<CartItem> items() {
        return items.values().stream()
                .sorted(Comparator.comparing(CartItem::updatedAt).reversed()
                        .thenComparing(CartItem::variantId))
                .toList();
    }

    public UUID id() { return id; }
    public UUID ownerId() { return ownerId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
