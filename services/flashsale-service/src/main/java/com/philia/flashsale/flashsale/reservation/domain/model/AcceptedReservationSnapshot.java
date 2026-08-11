package com.philia.flashsale.flashsale.reservation.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable snapshot handed from the hot path to durable acceptance. */
public record AcceptedReservationSnapshot(
        UUID purchaseRequestId,
        UUID reservationId,
        UUID eventId,
        UUID campaignId,
        UUID variantId,
        UUID userId,
        UUID inventoryAllocationId,
        String skuSnapshot,
        BigDecimal unitPrice,
        String currency,
        long quantity,
        String requestHash,
        String idempotencyKeyHash,
        Instant acceptedAt,
        Instant expiresAt,
        Instant retainedUntil,
        String traceparent,
        String tracestate) {

    public AcceptedReservationSnapshot {
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(inventoryAllocationId, "inventoryAllocationId");
        Objects.requireNonNull(skuSnapshot, "skuSnapshot");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(requestHash, "requestHash");
        Objects.requireNonNull(idempotencyKeyHash, "idempotencyKeyHash");
        Objects.requireNonNull(acceptedAt, "acceptedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(retainedUntil, "retainedUntil");
        if (skuSnapshot.isBlank() || skuSnapshot.length() > 120) {
            throw new IllegalArgumentException("skuSnapshot must contain 1..120 characters");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice cannot be negative");
        }
        unitPrice = unitPrice.setScale(4, RoundingMode.UNNECESSARY);
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be three uppercase letters");
        }
        if (!acceptedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("acceptedAt must be before expiresAt");
        }
        if (requestHash.length() != 64 || idempotencyKeyHash.length() != 64) {
            throw new IllegalArgumentException("request hashes must be SHA-256 hex values");
        }
    }
}
