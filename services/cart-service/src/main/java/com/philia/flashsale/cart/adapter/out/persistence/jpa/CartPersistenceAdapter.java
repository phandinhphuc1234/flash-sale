package com.philia.flashsale.cart.adapter.out.persistence.jpa;

import com.philia.flashsale.cart.adapter.out.persistence.jpa.entity.CartItemJpaEntity;
import com.philia.flashsale.cart.adapter.out.persistence.jpa.entity.CartJpaEntity;
import com.philia.flashsale.cart.adapter.out.persistence.jpa.repository.CartItemJpaRepository;
import com.philia.flashsale.cart.adapter.out.persistence.jpa.repository.CartJpaRepository;
import com.philia.flashsale.cart.application.port.out.MaintainCartPort;
import com.philia.flashsale.cart.application.result.CartItemState;
import com.philia.flashsale.cart.domain.valueobject.CartQuantity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

/** Short Cart-owned transactions; no Product network call is made inside this adapter. */
@Repository
@ConditionalOnProperty(name = "cart.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class CartPersistenceAdapter implements MaintainCartPort {
    private final CartJpaRepository carts;
    private final CartItemJpaRepository items;

    public CartPersistenceAdapter(CartJpaRepository carts, CartItemJpaRepository items) {
        this.carts = Objects.requireNonNull(carts, "carts");
        this.items = Objects.requireNonNull(items, "items");
    }

    @Override
    @Transactional
    public CartItemState upsertItem(UUID ownerId, UUID variantId, CartQuantity quantity, Instant now) {
        UUID candidateCartId = UUID.randomUUID();
        carts.upsertOwner(candidateCartId, ownerId, now, now);
        CartJpaEntity cart = carts.findByOwnerId(ownerId)
                .orElseThrow(() -> new IllegalStateException("Cart owner upsert did not return a Cart"));
        items.upsert(cart.getId(), variantId, quantity.value(), now, now);
        CartItemJpaEntity item = items.findByIdCartIdAndIdVariantId(cart.getId(), variantId)
                .orElseThrow(() -> new IllegalStateException("Cart item upsert did not return an item"));
        return new CartItemState(item.getVariantId(), CartQuantity.of(item.getQuantity()), item.getUpdatedAt());
    }

    @Override
    @Transactional
    public void removeItem(UUID ownerId, UUID variantId, Instant now) {
        carts.findByOwnerId(ownerId).ifPresent(cart -> {
            items.deleteByCartIdAndVariantId(cart.getId(), variantId);
            carts.touchOwner(ownerId, now);
        });
    }
}
