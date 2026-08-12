package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import java.sql.Types;
import org.hibernate.annotations.JdbcTypeCode;

/** Composite durable identity for user + campaign + hashed idempotency key. */
@Embeddable
public class PurchaseIdempotencyJpaId implements Serializable {
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "campaign_id", nullable = false) private UUID campaignId;
    @JdbcTypeCode(Types.CHAR) @Column(name = "idempotency_key_hash", nullable = false, length = 64) private String idempotencyKeyHash;

    protected PurchaseIdempotencyJpaId() { }
    public PurchaseIdempotencyJpaId(UUID userId, UUID campaignId, String idempotencyKeyHash) {
        this.userId = userId; this.campaignId = campaignId; this.idempotencyKeyHash = idempotencyKeyHash;
    }
    public UUID getUserId() { return userId; }
    public UUID getCampaignId() { return campaignId; }
    public String getIdempotencyKeyHash() { return idempotencyKeyHash; }
    @Override public boolean equals(Object o) { if (this == o) return true; if (!(o instanceof PurchaseIdempotencyJpaId other)) return false; return Objects.equals(userId, other.userId) && Objects.equals(campaignId, other.campaignId) && Objects.equals(idempotencyKeyHash, other.idempotencyKeyHash); }
    @Override public int hashCode() { return Objects.hash(userId, campaignId, idempotencyKeyHash); }
}
