package com.philia.flashsale.cart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.cart.application.exception.ProductDisplayDependencyException;
import com.philia.flashsale.cart.application.port.out.LoadCartPort;
import com.philia.flashsale.cart.application.port.out.LoadProductDisplaysPort;
import com.philia.flashsale.cart.application.result.CartResult;
import com.philia.flashsale.cart.application.result.ProductDisplay;
import com.philia.flashsale.cart.application.result.ProductDisplayBatch;
import com.philia.flashsale.cart.application.usecase.GetCartService;
import com.philia.flashsale.cart.application.query.GetCartQuery;
import com.philia.flashsale.cart.domain.model.Cart;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetCartUseCaseTests {
    private final LoadCartPort carts = org.mockito.Mockito.mock(LoadCartPort.class);
    private final LoadProductDisplaysPort products = org.mockito.Mockito.mock(LoadProductDisplaysPort.class);
    private GetCartService service;

    @BeforeEach
    void setUp() {
        service = new GetCartService(carts, products);
    }

    @Test
    void absentCartIsEmptyAndDoesNotCallProduct() {
        UUID owner = UUID.randomUUID();
        when(carts.load(owner)).thenReturn(Optional.empty());

        CartResult result = service.get(new GetCartQuery(owner, "trace-empty"));

        assertThat(result.items()).isEmpty();
        assertThat(result.distinctItemCount()).isZero();
        assertThat(result.totalQuantity()).isZero();
        assertThat(result.updatedAt()).isNull();
        verify(products, never()).load(anyList(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void readsItemsInStableOrderWithOneProductBatch() {
        UUID owner = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID olderVariant = UUID.randomUUID();
        UUID newerVariant = UUID.randomUUID();
        Instant created = Instant.parse("2026-08-29T00:00:00Z");
        Instant older = created.plusSeconds(1);
        Instant newer = created.plusSeconds(2);
        Cart cart = Cart.restore(cartId, owner, created, newer,
                List.of(com.philia.flashsale.cart.domain.model.CartItem.restore(olderVariant,
                                com.philia.flashsale.cart.domain.valueobject.CartQuantity.of(2), created, older),
                        com.philia.flashsale.cart.domain.model.CartItem.restore(newerVariant,
                                com.philia.flashsale.cart.domain.valueobject.CartQuantity.of(3), created, newer)));
        when(carts.load(owner)).thenReturn(Optional.of(cart));
        ProductDisplay newerDisplay = display(newerVariant, "newer", true);
        ProductDisplay olderDisplay = display(olderVariant, "older", true);
        when(products.load(List.of(newerVariant, olderVariant), "trace-read"))
                .thenReturn(ProductDisplayBatch.available(List.of(newerDisplay, olderDisplay)));

        CartResult result = service.get(new GetCartQuery(owner, "trace-read"));

        assertThat(result.items()).extracting(item -> item.variantId())
                .containsExactly(newerVariant, olderVariant);
        assertThat(result.items()).extracting(item -> item.quantity()).containsExactly(3, 2);
        assertThat(result.totalQuantity()).isEqualTo(5);
        assertThat(result.distinctItemCount()).isEqualTo(2);
        assertThat(result.items().get(0).productName()).isEqualTo("newer");
        verify(products).load(List.of(newerVariant, olderVariant), "trace-read");
    }

    @Test
    void readsCurrentProductDetailsOnEveryRequest() {
        UUID owner = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        Cart cart = Cart.restore(UUID.randomUUID(), owner,
                Instant.parse("2026-08-29T00:00:00Z"), Instant.parse("2026-08-29T00:00:00Z"),
                List.of(com.philia.flashsale.cart.domain.model.CartItem.restore(variant,
                        com.philia.flashsale.cart.domain.valueobject.CartQuantity.of(1),
                        Instant.parse("2026-08-29T00:00:00Z"), Instant.parse("2026-08-29T00:00:00Z"))));
        when(carts.load(owner)).thenReturn(Optional.of(cart));
        when(products.load(List.of(variant), "trace-refresh"))
                .thenReturn(ProductDisplayBatch.available(List.of(display(variant, "before", true))))
                .thenReturn(ProductDisplayBatch.available(List.of(display(variant, "after", true))));

        assertThat(service.get(new GetCartQuery(owner, "trace-refresh")).items().get(0).productName())
                .isEqualTo("before");
        assertThat(service.get(new GetCartQuery(owner, "trace-refresh")).items().get(0).productName())
                .isEqualTo("after");
    }

    @Test
    void productFailureKeepsSavedIntentAndHidesProductFields() {
        UUID owner = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        Cart cart = cartWithSingleItem(owner, variant);
        when(carts.load(owner)).thenReturn(Optional.of(cart));
        when(products.load(List.of(variant), "trace-outage"))
                .thenThrow(new ProductDisplayDependencyException(
                        ProductDisplayDependencyException.Failure.TIMEOUT));

        CartResult result = service.get(new GetCartQuery(owner, "trace-outage"));

        var item = result.items().get(0);
        assertThat(item.quantity()).isEqualTo(1);
        assertThat(item.detailsAvailable()).isFalse();
        assertThat(item.sellable()).isNull();
        assertThat(item.productId()).isNull();
        assertThat(item.unavailableReason()).isEqualTo("PRODUCT_DETAILS_UNAVAILABLE");
    }

    @Test
    void missingAndNonSellableResultsHaveDistinctUnavailableReasons() {
        UUID owner = UUID.randomUUID();
        UUID missingVariant = UUID.randomUUID();
        UUID unavailableVariant = UUID.randomUUID();
        Cart cart = Cart.restore(UUID.randomUUID(), owner,
                Instant.parse("2026-08-29T00:00:00Z"), Instant.parse("2026-08-29T00:00:00Z"),
                List.of(
                        com.philia.flashsale.cart.domain.model.CartItem.restore(missingVariant,
                                com.philia.flashsale.cart.domain.valueobject.CartQuantity.of(1),
                                Instant.parse("2026-08-29T00:00:00Z"), Instant.parse("2026-08-29T00:00:00Z")),
                        com.philia.flashsale.cart.domain.model.CartItem.restore(unavailableVariant,
                                com.philia.flashsale.cart.domain.valueobject.CartQuantity.of(1),
                                Instant.parse("2026-08-29T00:00:00Z"), Instant.parse("2026-08-29T00:00:01Z"))));
        when(carts.load(owner)).thenReturn(Optional.of(cart));
        when(products.load(List.of(unavailableVariant, missingVariant), "trace-state"))
                .thenReturn(ProductDisplayBatch.available(List.of(
                        display(unavailableVariant, "unavailable", false),
                        ProductDisplay.missing(missingVariant))));

        CartResult result = service.get(new GetCartQuery(owner, "trace-state"));

        assertThat(result.items().get(0).unavailableReason()).isEqualTo("PRODUCT_NOT_SELLABLE");
        assertThat(result.items().get(0).detailsAvailable()).isTrue();
        assertThat(result.items().get(1).unavailableReason()).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(result.items().get(1).detailsAvailable()).isFalse();
    }

    private static Cart cartWithSingleItem(UUID owner, UUID variant) {
        Instant now = Instant.parse("2026-08-29T00:00:00Z");
        return Cart.restore(UUID.randomUUID(), owner, now, now,
                List.of(com.philia.flashsale.cart.domain.model.CartItem.restore(variant,
                        com.philia.flashsale.cart.domain.valueobject.CartQuantity.of(1), now, now)));
    }

    private static ProductDisplay display(UUID variant, String name, boolean sellable) {
        return new ProductDisplay(variant, true, sellable, UUID.randomUUID(), "slug-" + name,
                name, "variant", "sku-" + name, BigDecimal.TEN, "VND", null);
    }
}
