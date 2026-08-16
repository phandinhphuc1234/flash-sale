package com.philia.flashsale.order.order.application.result;

import com.philia.flashsale.order.order.domain.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Application-owned detail read model; no JPA or HTTP types cross this boundary. */
public record OrderDetailsResult(
        UUID id,
        String orderNumber,
        UUID purchaseRequestId,
        UUID reservationId,
        UUID campaignId,
        OrderStatus status,
        String currency,
        BigDecimal subtotalAmount,
        BigDecimal totalAmount,
        Instant acceptedAt,
        Instant reservationExpiresAt,
        List<OrderItemResult> items,
        Instant createdAt,
        Instant updatedAt) {
    public OrderDetailsResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(orderNumber, "orderNumber");
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(subtotalAmount, "subtotalAmount");
        Objects.requireNonNull(totalAmount, "totalAmount");
        Objects.requireNonNull(acceptedAt, "acceptedAt");
        Objects.requireNonNull(reservationExpiresAt, "reservationExpiresAt");
        items = items == null ? List.of() : List.copyOf(items);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
