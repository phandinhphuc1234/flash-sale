package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import java.sql.Types;
import org.hibernate.annotations.JdbcTypeCode;

/**
 * Durable request audit row.
 * It keeps the immutable Redis winner and its terminal outcome.
 */
@Entity
@Table(name = "purchase_requests")
public class PurchaseRequestJpaEntity {
    @Id
    private UUID id;
    @Column(name = "reservation_id", nullable = false, unique = true)
    private UUID reservationId;
    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;
    @Column(name = "variant_id", nullable = false)
    private UUID variantId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private long quantity;
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Outcome outcome;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "accepted_at")
    private Instant acceptedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PurchaseRequestJpaEntity() {
    }

    public static PurchaseRequestJpaEntity accepted(AcceptedReservationSnapshot snapshot) {
        var entity = new PurchaseRequestJpaEntity();
        entity.id = snapshot.purchaseRequestId();
        entity.reservationId = snapshot.reservationId();
        entity.campaignId = snapshot.campaignId();
        entity.variantId = snapshot.variantId();
        entity.userId = snapshot.userId();
        entity.quantity = snapshot.quantity();
        entity.requestHash = snapshot.requestHash();
        entity.outcome = Outcome.ACCEPTED;
        entity.expiresAt = snapshot.expiresAt();
        entity.acceptedAt = snapshot.acceptedAt();
        entity.createdAt = snapshot.acceptedAt();
        entity.updatedAt = snapshot.acceptedAt();
        return entity;
    }

    /**
     * The terminal tombstone that wins if durable acceptance reaches PostgreSQL
     * after expiry.
     */
    public static PurchaseRequestJpaEntity expired(AcceptedReservationSnapshot snapshot, Instant at) {
        var entity = accepted(snapshot);
        entity.outcome = Outcome.EXPIRED;
        entity.acceptedAt = null;
        entity.createdAt = at;
        entity.updatedAt = at;
        return entity;
    }

    public void markExpired(Instant at) {
        if (outcome == Outcome.ACCEPTED)
            return;
        outcome = Outcome.EXPIRED;
        acceptedAt = null;
        updatedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public UUID getCampaignId() { return campaignId; }
    public UUID getVariantId() { return variantId; }
    public UUID getUserId() { return userId; }
    public long getQuantity() { return quantity; }
    public String getRequestHash() { return requestHash; }
    public Outcome getOutcome() { return outcome; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; }

    public enum Outcome { ACCEPTED, EXPIRED }
}
