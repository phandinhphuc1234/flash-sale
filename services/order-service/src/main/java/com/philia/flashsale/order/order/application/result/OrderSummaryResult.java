package com.philia.flashsale.order.order.application.result;

import com.philia.flashsale.order.order.domain.model.OrderStatus;
import com.philia.flashsale.order.order.domain.model.PurchaseSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Application-owned list read model intentionally omitting owner and internal identities. */
public record OrderSummaryResult(
        UUID id,
        String orderNumber,
        OrderStatus status,
        PurchaseSource purchaseSource,
        String currency,
        BigDecimal totalAmount,
        Instant reservationExpiresAt,
        Instant createdAt) {
    public OrderSummaryResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(orderNumber, "orderNumber");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(purchaseSource, "purchaseSource");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(totalAmount, "totalAmount");
        Objects.requireNonNull(reservationExpiresAt, "reservationExpiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
