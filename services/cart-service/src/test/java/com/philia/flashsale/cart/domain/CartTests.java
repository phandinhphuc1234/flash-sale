package com.philia.flashsale.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.cart.domain.model.Cart;
import com.philia.flashsale.cart.domain.valueobject.CartQuantity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartTests {
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    @Test
    void acceptsOnlyQuantitiesFromOneThroughTen() {
        assertThat(CartQuantity.of(1).value()).isEqualTo(1);
        assertThat(CartQuantity.of(10).value()).isEqualTo(10);
        assertThatThrownBy(() -> CartQuantity.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CartQuantity.of(11)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replacementMaintainsOneItemPerVariantAndUpdatesQuantity() {
        UUID variant = UUID.randomUUID();
        Cart cart = Cart.create(UUID.randomUUID(), UUID.randomUUID(), NOW);

        cart.setItem(variant, CartQuantity.of(2), NOW);
        cart.setItem(variant, CartQuantity.of(5), NOW.plusSeconds(1));

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().getFirst().quantity().value()).isEqualTo(5);
        assertThat(cart.items().getFirst().updatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void removingMissingItemIsAnIdempotentNoOp() {
        Cart cart = Cart.create(UUID.randomUUID(), UUID.randomUUID(), NOW);
        cart.removeItem(UUID.randomUUID(), NOW.plusSeconds(1));
        assertThat(cart.items()).isEmpty();
    }
}
