package com.philia.flashsale.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.cart.domain.model.Cart;
import com.philia.flashsale.cart.domain.valueobject.CartQuantity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartCheckoutRevisionTests {
    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

    @Test
    void quantityReplacementAdvancesCartAndItemRevisionsTogether() {
        UUID variant = UUID.randomUUID();
        Cart cart = Cart.create(UUID.randomUUID(), UUID.randomUUID(), NOW);

        cart.setItem(variant, CartQuantity.of(1), NOW);
        long firstCartVersion = cart.version();
        long firstItemVersion = cart.items().getFirst().version();

        cart.setItem(variant, CartQuantity.of(3), NOW.plusSeconds(1));

        assertThat(cart.version()).isEqualTo(firstCartVersion + 1);
        assertThat(cart.items().getFirst().version()).isEqualTo(firstItemVersion + 1);
        assertThat(cart.items().getFirst().quantity().value()).isEqualTo(3);
    }

    @Test
    void deleteAndReAddGetsANewerItemRevision() {
        UUID variant = UUID.randomUUID();
        Cart cart = Cart.create(UUID.randomUUID(), UUID.randomUUID(), NOW);

        cart.setItem(variant, CartQuantity.of(1), NOW);
        long deletedRevision = cart.items().getFirst().version();
        long cartBeforeDelete = cart.version();
        cart.removeItem(variant, NOW.plusSeconds(1));
        cart.setItem(variant, CartQuantity.of(2), NOW.plusSeconds(2));

        assertThat(cart.version()).isEqualTo(cartBeforeDelete + 2);
        assertThat(cart.items().getFirst().version()).isGreaterThan(deletedRevision);
        assertThat(cart.items().getFirst().quantity().value()).isEqualTo(2);
    }

    @Test
    void removingMissingItemDoesNotAdvanceRevision() {
        Cart cart = Cart.create(UUID.randomUUID(), UUID.randomUUID(), NOW);

        cart.removeItem(UUID.randomUUID(), NOW.plusSeconds(1));

        assertThat(cart.version()).isZero();
        assertThat(cart.items()).isEmpty();
    }
}
