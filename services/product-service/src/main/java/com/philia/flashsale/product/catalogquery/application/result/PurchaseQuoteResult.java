package com.philia.flashsale.product.catalogquery.application.result;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable current commercial decision for one requested Product variant. */
public record PurchaseQuoteResult(
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

    public static PurchaseQuoteResult missing(UUID variantId) {
        return new PurchaseQuoteResult(
                variantId, false, false, null, null, null, null, null, null, null, null);
    }
}
