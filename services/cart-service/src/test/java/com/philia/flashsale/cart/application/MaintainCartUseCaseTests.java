package com.philia.flashsale.cart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.philia.flashsale.cart.application.command.RemoveCartItemCommand;
import com.philia.flashsale.cart.application.command.SetCartItemCommand;
import com.philia.flashsale.cart.application.exception.CartVariantNotFoundException;
import com.philia.flashsale.cart.application.exception.CartVariantNotSellableException;
import com.philia.flashsale.cart.application.exception.ProductDisplayDependencyException;
import com.philia.flashsale.cart.application.port.out.LoadProductDisplaysPort;
import com.philia.flashsale.cart.application.port.out.MaintainCartPort;
import com.philia.flashsale.cart.application.result.CartItemState;
import com.philia.flashsale.cart.application.result.ProductDisplay;
import com.philia.flashsale.cart.application.result.ProductDisplayBatch;
import com.philia.flashsale.cart.application.usecase.MaintainCartService;
import com.philia.flashsale.cart.domain.valueobject.CartQuantity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaintainCartUseCaseTests {
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");
    private final LoadProductDisplaysPort products = mock(LoadProductDisplaysPort.class);
    private final MaintainCartPort cart = mock(MaintainCartPort.class);
    private final MaintainCartService service = new MaintainCartService(products, cart,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void verifiesProductBeforePersistenceAndReturnsSavedIntent() {
        UUID owner = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        ProductDisplay display = new ProductDisplay(variant, true, true, UUID.randomUUID(), "slug",
                "Product", "Variant", "SKU", null, "VND", null);
        when(products.load(List.of(variant), "trace-1")).thenReturn(ProductDisplayBatch.available(List.of(display)));
        when(cart.upsertItem(owner, variant, CartQuantity.of(2), NOW))
                .thenReturn(new CartItemState(variant, CartQuantity.of(2), NOW));

        var result = service.set(new SetCartItemCommand(owner, variant, 2, "trace-1"));

        assertThat(result.variantId()).isEqualTo(variant);
        assertThat(result.quantity()).isEqualTo(2);
        var order = inOrder(products, cart);
        order.verify(products).load(List.of(variant), "trace-1");
        order.verify(cart).upsertItem(owner, variant, CartQuantity.of(2), NOW);
    }

    @Test
    void productRejectionDoesNotTouchCart() {
        UUID owner = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        when(products.load(List.of(variant), null))
                .thenReturn(ProductDisplayBatch.available(List.of(ProductDisplay.missing(variant))));
        assertThatThrownBy(() -> service.set(new SetCartItemCommand(owner, variant, 1, null)))
                .isInstanceOf(CartVariantNotFoundException.class);
        verifyNoInteractions(cart);

        ProductDisplay notSellable = new ProductDisplay(variant, true, false, UUID.randomUUID(), "slug",
                "Product", "Variant", "SKU", null, "VND", null);
        when(products.load(List.of(variant), null))
                .thenReturn(ProductDisplayBatch.available(List.of(notSellable)));
        assertThatThrownBy(() -> service.set(new SetCartItemCommand(owner, variant, 1, null)))
                .isInstanceOf(CartVariantNotSellableException.class);
        verifyNoInteractions(cart);
    }

    @Test
    void productDependencyFailureDoesNotTouchCartAndRemoveIsIdempotent() {
        UUID owner = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        when(products.load(List.of(variant), "trace"))
                .thenThrow(new ProductDisplayDependencyException(
                        ProductDisplayDependencyException.Failure.TIMEOUT));
        assertThatThrownBy(() -> service.set(new SetCartItemCommand(owner, variant, 1, "trace")))
                .isInstanceOf(ProductDisplayDependencyException.class);
        verifyNoInteractions(cart);

        service.remove(new RemoveCartItemCommand(owner, variant));
        org.mockito.Mockito.verify(cart).removeItem(owner, variant, NOW);
    }
}
