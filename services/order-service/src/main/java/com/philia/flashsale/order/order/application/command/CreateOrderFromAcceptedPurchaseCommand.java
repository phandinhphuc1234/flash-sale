package com.philia.flashsale.order.order.application.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Framework-free command representing the validated PurchaseAccepted business snapshot. */
public record CreateOrderFromAcceptedPurchaseCommand(
        UUID eventId,
        String eventType,
        int eventVersion,
        String producer,
        String aggregateType,
        UUID aggregateId,
        long aggregateVersion,
        UUID correlationId,
        UUID causationId,
        Instant eventOccurredAt,
        UUID purchaseRequestId,
        UUID reservationId,
        UUID campaignId,
        UUID variantId,
        UUID userId,
        long quantity,
        BigDecimal unitPrice,
        String currency,
        Instant acceptedAt,
        Instant expiresAt,
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String traceparent,
        String tracestate) {

    public CreateOrderFromAcceptedPurchaseCommand {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(eventOccurredAt, "eventOccurredAt");
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(acceptedAt, "acceptedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(sourceTopic, "sourceTopic");
        if (quantity <= 0 || aggregateVersion <= 0 || eventVersion <= 0) {
            throw new IllegalArgumentException("event versions and quantity must be positive");
        }
        if (sourcePartition < 0 || sourceOffset < 0) {
            throw new IllegalArgumentException("source position must not be negative");
        }
    }
}
