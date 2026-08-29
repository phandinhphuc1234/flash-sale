package com.philia.flashsale.cart.adapter.out.client.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.cart.application.exception.ProductDisplayDependencyException;
import com.philia.flashsale.cart.configuration.CartProductServiceTokenException;
import com.philia.flashsale.cart.configuration.CartProductServiceTokenManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductDisplayClientAdapterTests {

    @Test
    void sendsOneDeduplicatedBatchWithMachineAuthorizationAndTrace() {
        ProductDisplayFeignClient client = mock(ProductDisplayFeignClient.class);
        CartProductServiceTokenManager tokenManager = mock(CartProductServiceTokenManager.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(tokenManager.authorizationHeader()).thenReturn("Bearer machine-token");
        when(client.displayDetails("Bearer machine-token", "trace-cart",
                new ProductDisplayWireModels.Request(List.of(first, second))))
                .thenReturn(new ProductDisplayWireModels.Envelope(true, "SUCCESS", "ok",
                        new ProductDisplayWireModels.Data(List.of(
                                found(first), missing(second))), null));

        var result = new ProductDisplayClientAdapter(client, tokenManager)
                .load(List.of(first, second, first), "trace-cart");

        assertThat(result.detailsAvailable()).isTrue();
        assertThat(result.displays()).extracting(display -> display.variantId())
                .containsExactly(first, second);
        verify(client).displayDetails("Bearer machine-token", "trace-cart",
                new ProductDisplayWireModels.Request(List.of(first, second)));
    }

    @Test
    void rejectsIdentityMismatchedSuccessInsteadOfAcceptingUnrelatedProductData() {
        ProductDisplayFeignClient client = mock(ProductDisplayFeignClient.class);
        CartProductServiceTokenManager tokenManager = mock(CartProductServiceTokenManager.class);
        UUID requested = UUID.randomUUID();
        UUID returned = UUID.randomUUID();
        when(tokenManager.authorizationHeader()).thenReturn("Bearer machine-token");
        when(client.displayDetails(any(), any(), any()))
                .thenReturn(new ProductDisplayWireModels.Envelope(true, "SUCCESS", "ok",
                        new ProductDisplayWireModels.Data(List.of(missing(returned))), null));

        assertThatThrownBy(() -> new ProductDisplayClientAdapter(client, tokenManager)
                .load(List.of(requested), "trace-cart"))
                .isInstanceOfSatisfying(ProductDisplayDependencyException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(ProductDisplayDependencyException.Failure.MALFORMED_RESPONSE));
    }

    @Test
    void mapsRemoteAuthorizationAndTokenFailuresToApplicationOwnedOutcomes() {
        ProductDisplayFeignClient client = mock(ProductDisplayFeignClient.class);
        CartProductServiceTokenManager tokenManager = mock(CartProductServiceTokenManager.class);
        UUID variantId = UUID.randomUUID();
        when(tokenManager.authorizationHeader()).thenReturn("Bearer machine-token");
        when(client.displayDetails(any(), any(), any()))
                .thenThrow(new ProductDisplayRemoteException(ProductDisplayRemoteException.Failure.FORBIDDEN));

        assertThatThrownBy(() -> new ProductDisplayClientAdapter(client, tokenManager)
                .load(List.of(variantId), "trace-cart"))
                .isInstanceOfSatisfying(ProductDisplayDependencyException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(ProductDisplayDependencyException.Failure.FORBIDDEN));

        when(tokenManager.authorizationHeader()).thenThrow(new CartProductServiceTokenException("unavailable"));
        assertThatThrownBy(() -> new ProductDisplayClientAdapter(client, tokenManager)
                .load(List.of(variantId), "trace-cart"))
                .isInstanceOfSatisfying(ProductDisplayDependencyException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(ProductDisplayDependencyException.Failure.TOKEN_UNAVAILABLE));
    }

    private static ProductDisplayWireModels.Variant found(UUID variantId) {
        return new ProductDisplayWireModels.Variant(variantId, true, true, UUID.randomUUID(), "slug",
                "Product", "Variant", "SKU", new BigDecimal("10.00"), "VND", null);
    }

    private static ProductDisplayWireModels.Variant missing(UUID variantId) {
        return new ProductDisplayWireModels.Variant(variantId, false, false, null, null, null, null,
                null, null, null, null);
    }
}
