package com.philia.flashsale.flashsale.reservation.domain.model;

import com.philia.flashsale.flashsale.reservation.domain.exception.InvalidReservationStateException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Stable logical purchase request whose durable outcome cannot be resurrected after expiry. */
public final class PurchaseRequest {
    private final UUID id;
    private final UUID reservationId;
    private final UUID campaignId;
    private final UUID variantId;
    private final UUID userId;
    private final long quantity;
    private final String requestHash;
    private final Instant expiresAt;
    private PurchaseOutcome outcome;
    private Instant acceptedAt;

    private PurchaseRequest(UUID id, UUID reservationId, UUID campaignId, UUID variantId, UUID userId,
            long quantity, String requestHash, Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        this.campaignId = Objects.requireNonNull(campaignId, "campaignId");
        this.variantId = Objects.requireNonNull(variantId, "variantId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.quantity = quantity;
        this.outcome = null;
    }

    public static PurchaseRequest pending(UUID id, UUID reservationId, UUID campaignId, UUID variantId,
            UUID userId, long quantity, String requestHash, Instant expiresAt) {
        return new PurchaseRequest(id, reservationId, campaignId, variantId, userId, quantity, requestHash,
                expiresAt);
    }

    public void accept(Instant acceptedAt) {
        Objects.requireNonNull(acceptedAt, "acceptedAt");
        if (outcome == PurchaseOutcome.EXPIRED) {
            throw new InvalidReservationStateException("An expired purchase request cannot be accepted");
        }
        if (!acceptedAt.isBefore(expiresAt)) {
            throw new InvalidReservationStateException("A purchase request cannot be accepted after expiry");
        }
        outcome = PurchaseOutcome.ACCEPTED;
        this.acceptedAt = acceptedAt;
    }

    public void expire() {
        if (outcome == PurchaseOutcome.ACCEPTED) {
            throw new InvalidReservationStateException("An accepted purchase request cannot be expired");
        }
        outcome = PurchaseOutcome.EXPIRED;
        acceptedAt = null;
    }

    public UUID id() { return id; }
    public UUID reservationId() { return reservationId; }
    public UUID campaignId() { return campaignId; }
    public UUID variantId() { return variantId; }
    public UUID userId() { return userId; }
    public long quantity() { return quantity; }
    public String requestHash() { return requestHash; }
    public Instant expiresAt() { return expiresAt; }
    public PurchaseOutcome outcome() { return outcome; }
    public Instant acceptedAt() { return acceptedAt; }
}
