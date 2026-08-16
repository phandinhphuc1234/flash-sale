package com.philia.flashsale.order.order.application.result;

import java.math.BigDecimal;
import java.util.UUID;

/** Application read model for the immutable one-item commercial snapshot. */
public record OrderItemResult(
        UUID variantId,
        long quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount) {
}
