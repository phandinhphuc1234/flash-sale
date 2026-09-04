package com.philia.flashsale.order.regularpurchase.adapter.out.client.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDownstreamException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Boundary tests for exact Product wire identity and sanitized remote failure translation. */
class ProductPurchaseQuoteClientAdapterTests {

    private final ProductPurchaseQuoteFeignClient client = mock(ProductPurchaseQuoteFeignClient.class);
    private final ProductPurchaseQuoteClientAdapter adapter = new ProductPurchaseQuoteClientAdapter(client);

    @Test
    void sortsTheBoundedBatchAndMapsCompatibleQuotes() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var firstQuote = quote(first);
        var secondQuote = quote(second);
        when(client.purchaseQuotes(eq("trace-1"), any())).thenReturn(ApiResponse.success(
                new ProductPurchaseQuoteBatchResponse(List.of(firstQuote, secondQuote))));

        var quotes = adapter.loadQuotes(List.of(second, first), "trace-1");

        ArgumentCaptor<ProductPurchaseQuoteBatchRequest> request =
                ArgumentCaptor.forClass(ProductPurchaseQuoteBatchRequest.class);
        verify(client).purchaseQuotes(eq("trace-1"), request.capture());
        assertThat(request.getValue().variantIds()).containsExactly(first, second);
        assertThat(quotes).extracting(quote -> quote.variantId()).containsExactly(first, second);
        assertThat(quotes.getFirst().unitPrice().amount()).isEqualByComparingTo("179000.0000");
    }

    @Test
    void rejectsAnIncompleteOrIdentityMismatchedQuoteResponse() {
        UUID expected = UUID.randomUUID();
        when(client.purchaseQuotes(any(), any())).thenReturn(ApiResponse.success(
                new ProductPurchaseQuoteBatchResponse(List.of(quote(UUID.randomUUID())))));

        assertThatThrownBy(() -> adapter.loadQuotes(List.of(expected), "trace-2"))
                .isInstanceOf(RegularPurchaseDownstreamException.class)
                .extracting(exception -> ((RegularPurchaseDownstreamException) exception).failure())
                .isEqualTo(RegularPurchaseDownstreamException.Failure.PRODUCT_SERVICE_UNAVAILABLE);
    }

    @Test
    void mapsAStableProductValidationErrorWithoutRemoteMessageText() {
        when(client.purchaseQuotes(any(), any())).thenThrow(new ProductPurchaseQuoteRemoteException(
                ProductPurchaseQuoteRemoteException.Failure.REJECTED, 400));

        assertThatThrownBy(() -> adapter.loadQuotes(List.of(UUID.randomUUID()), "trace-3"))
                .isInstanceOf(RegularPurchaseDownstreamException.class)
                .extracting(exception -> ((RegularPurchaseDownstreamException) exception).failure())
                .isEqualTo(RegularPurchaseDownstreamException.Failure.PRODUCT_QUOTE_REJECTED);
    }

    private ProductPurchaseQuoteBatchResponse.ProductPurchaseQuoteResponse quote(UUID variantId) {
        return new ProductPurchaseQuoteBatchResponse.ProductPurchaseQuoteResponse(variantId, true, true, null,
                UUID.randomUUID(), "SKU-1", "Product", "Variant", new BigDecimal("179000.0000"), "VND", 7L);
    }
}
