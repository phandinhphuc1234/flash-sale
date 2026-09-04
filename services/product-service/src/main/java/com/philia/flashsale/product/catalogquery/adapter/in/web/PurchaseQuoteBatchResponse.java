package com.philia.flashsale.product.catalogquery.adapter.in.web;

import com.philia.flashsale.product.catalogquery.application.result.PurchaseQuoteResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** HTTP representation of Product's authoritative current purchase decision. */
public record PurchaseQuoteBatchResponse(List<PurchaseQuoteResponse> quotes) {

    static PurchaseQuoteBatchResponse from(List<PurchaseQuoteResult> results) {
        return new PurchaseQuoteBatchResponse(results.stream().map(PurchaseQuoteResponse::from).toList());
    }

    public record PurchaseQuoteResponse(
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

        private static PurchaseQuoteResponse from(PurchaseQuoteResult result) {
            return new PurchaseQuoteResponse(
                    result.variantId(),
                    result.found(),
                    result.sellable(),
                    result.unavailableReason(),
                    result.productId(),
                    result.sku(),
                    result.productName(),
                    result.variantName(),
                    result.unitPrice(),
                    result.currency(),
                    result.catalogVersion());
        }
    }
}
