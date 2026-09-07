package com.philia.flashsale.order.regularpurchase.adapter.out.client.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Wire response owned by the Order-to-Product adapter. */
public record ProductPurchaseQuoteBatchResponse(List<ProductPurchaseQuoteResponse> quotes) {
    public record ProductPurchaseQuoteResponse(
            UUID variantId,
            boolean found,
            boolean sellable,
            String unavailableReason,
            UUID productId,
            String sku,
            String productName,
            String variantName,
            BigDecimal unitPrice,
            String currency,
            Long catalogVersion) {
    }
}
