package com.philia.flashsale.order.regularpurchase.application.result;

import com.philia.flashsale.order.order.domain.model.PurchaseSource;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Shopper-safe accepted or replayed regular purchase projection. */
public record RegularPurchaseCheckoutResult(
        UUID purchaseRequestId,
        UUID orderId,
        PurchaseSource source,
        String status,
        String currency,
        Money totalAmount,
        Instant paymentDeadline,
        Instant stockHoldExpiresAt,
        boolean replayed) {

    public RegularPurchaseCheckoutResult {
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId is required");
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(currency, "currency is required");
        Objects.requireNonNull(totalAmount, "totalAmount is required");
        Objects.requireNonNull(paymentDeadline, "paymentDeadline is required");
        Objects.requireNonNull(stockHoldExpiresAt, "stockHoldExpiresAt is required");
    }
}
