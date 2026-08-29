package com.philia.flashsale.cart.adapter.out.persistence.jpa;

import com.philia.flashsale.cart.adapter.out.persistence.jpa.entity.CartItemJpaEntity;
import com.philia.flashsale.cart.adapter.out.persistence.jpa.entity.CartJpaEntity;
import com.philia.flashsale.cart.adapter.out.persistence.jpa.repository.CartItemJpaRepository;
import com.philia.flashsale.cart.adapter.out.persistence.jpa.repository.CartJpaRepository;
import com.philia.flashsale.cart.application.port.out.MaintainCartPort;
import com.philia.flashsale.cart.application.port.out.LoadCartPort;
import com.philia.flashsale.cart.application.result.CartItemState;
import com.philia.flashsale.cart.domain.model.Cart;
import com.philia.flashsale.cart.domain.model.CartItem;
import com.philia.flashsale.cart.domain.valueobject.CartQuantity;
import java.time.Instant;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

/** Short Cart-owned transactions; no Product network call is made inside this adapter. */
@Repository
@ConditionalOnProperty(name = "cart.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class CartPersistenceAdapter implements MaintainCartPort, LoadCartPort {
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

    @Override
    @Transactional(readOnly = true)
    public Optional<Cart> load(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return carts.findByOwnerId(ownerId).map(cart -> {
            List<CartItem> restoredItems = items.findByCartIdOrderByUpdatedAtDescVariantIdAsc(cart.getId())
                    .stream()
                    .map(item -> CartItem.restore(item.getVariantId(),
                            CartQuantity.of(item.getQuantity()), item.getCreatedAt(), item.getUpdatedAt()))
                    .toList();
            return Cart.restore(cart.getId(), cart.getOwnerId(), cart.getCreatedAt(), cart.getUpdatedAt(),
                    restoredItems);
        });
    }
}
