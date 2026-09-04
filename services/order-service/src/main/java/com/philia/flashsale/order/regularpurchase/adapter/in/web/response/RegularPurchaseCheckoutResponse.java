package com.philia.flashsale.order.regularpurchase.adapter.in.web.response;

import com.philia.flashsale.order.order.domain.model.PurchaseSource;
import com.philia.flashsale.order.regularpurchase.application.result.RegularPurchaseCheckoutResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Shopper-safe accepted regular purchase response; payment/provider details remain private. */
public record RegularPurchaseCheckoutResponse(
        UUID purchaseRequestId,
        UUID orderId,
        PurchaseSource source,
        String status,
        String currency,
        BigDecimal totalAmount,
        Instant paymentDeadline,
        Instant stockHoldExpiresAt) {

    public static RegularPurchaseCheckoutResponse from(RegularPurchaseCheckoutResult result) {
        return new RegularPurchaseCheckoutResponse(result.purchaseRequestId(), result.orderId(), result.source(),
                result.status(), result.currency(), result.totalAmount().amount(), result.paymentDeadline(),
                result.stockHoldExpiresAt());
    }
}
