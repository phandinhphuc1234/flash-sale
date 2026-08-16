package com.philia.flashsale.order.order.adapter.in.web.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Public line snapshot; the internal line identity is intentionally omitted. */
public record OrderItemResponse(
        UUID variantId,
        long quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount) {
}
