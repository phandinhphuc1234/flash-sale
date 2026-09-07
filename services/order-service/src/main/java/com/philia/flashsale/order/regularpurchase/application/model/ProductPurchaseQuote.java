package com.philia.flashsale.order.regularpurchase.application.model;

import com.philia.flashsale.order.order.domain.valueobject.Money;
import java.util.UUID;

/** Order-owned projection of Product's authoritative commercial decision for one variant. */
public record ProductPurchaseQuote(
        UUID variantId,
        boolean found,
        boolean sellable,
        String unavailableReason,
        UUID productId,
        String sku,
        String productName,
        String variantName,
        Money unitPrice,
        String currency,
        Long catalogVersion) {
}
