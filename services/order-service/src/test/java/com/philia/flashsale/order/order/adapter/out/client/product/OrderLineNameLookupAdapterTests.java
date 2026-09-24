package com.philia.flashsale.order.order.adapter.out.client.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.order.regularpurchase.adapter.out.client.product.ProductPurchaseQuoteBatchResponse;
import com.philia.flashsale.order.regularpurchase.adapter.out.client.product.ProductPurchaseQuoteFeignClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderLineNameLookupAdapterTests {

    private final ProductPurchaseQuoteFeignClient client = mock(ProductPurchaseQuoteFeignClient.class);
    private final OrderLineNameLookupAdapter adapter = new OrderLineNameLookupAdapter(client);

    @Test
    void returnsTrimmedTrustedNamesForTheRequestedVariant() {
        UUID variantId = UUID.randomUUID();
        when(client.purchaseQuotes(any(), any())).thenReturn(ApiResponse.success(
                new ProductPurchaseQuoteBatchResponse(List.of(quote(variantId, " Console ", " White ")))));

        var names = adapter.lookup(variantId, "trace-names").orElseThrow();

        assertThat(names.productName()).isEqualTo("Console");
        assertThat(names.variantName()).isEqualTo("White");
    }

    @Test
    void returnsEmptyForMissingMalformedOrFailedProductLookup() {
        UUID variantId = UUID.randomUUID();
        when(client.purchaseQuotes(any(), any()))
                .thenReturn(ApiResponse.success(new ProductPurchaseQuoteBatchResponse(
                        List.of(quote(UUID.randomUUID(), "Product", "Variant")))))
                .thenThrow(new IllegalStateException("provider detail must not escape"));

        assertThat(adapter.lookup(variantId, "trace-mismatch")).isEmpty();
        assertThat(adapter.lookup(variantId, "trace-failure")).isEmpty();
    }

    private ProductPurchaseQuoteBatchResponse.ProductPurchaseQuoteResponse quote(UUID variantId,
            String productName, String variantName) {
        return new ProductPurchaseQuoteBatchResponse.ProductPurchaseQuoteResponse(variantId, true, true, null,
                UUID.randomUUID(), "SKU-1", productName, variantName, new BigDecimal("179000.0000"), "VND", 1L);
    }
}
