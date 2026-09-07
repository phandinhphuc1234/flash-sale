package com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHoldStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "regular_stock_holds")
public class RegularStockHoldJpaEntity {
    @Id private UUID id;
    @Column(name = "purchase_request_id", nullable = false, unique = true) private UUID purchaseRequestId;
    @Column(name = "order_id", nullable = false, unique = true) private UUID orderId;
    @Column(name = "shopper_id", nullable = false) private UUID shopperId;
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private RegularStockHoldStatus status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @Column(name = "released_at") private Instant releasedAt;
    @Column(name = "expired_at") private Instant expiredAt;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected RegularStockHoldJpaEntity() { }

    public RegularStockHoldJpaEntity(UUID id, UUID purchaseRequestId, UUID orderId, UUID shopperId,
            String requestFingerprint, RegularStockHoldStatus status, Instant expiresAt,
            Instant confirmedAt, Instant releasedAt, Instant expiredAt, long version,
            Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.purchaseRequestId = purchaseRequestId;
        this.orderId = orderId;
        this.shopperId = shopperId;
        this.requestFingerprint = requestFingerprint;
        this.status = status;
        this.expiresAt = expiresAt;
        this.confirmedAt = confirmedAt;
        this.releasedAt = releasedAt;
        this.expiredAt = expiredAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getPurchaseRequestId() { return purchaseRequestId; }
    public UUID getOrderId() { return orderId; }
    public UUID getShopperId() { return shopperId; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public RegularStockHoldStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public Instant getExpiredAt() { return expiredAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Copies mutable aggregate state while retaining the row identity and JPA-managed version. */
    public void applyMutableState(RegularStockHoldStatus status, Instant confirmedAt, Instant releasedAt,
            Instant expiredAt, Instant updatedAt) {
        this.status = status;
        this.confirmedAt = confirmedAt;
        this.releasedAt = releasedAt;
        this.expiredAt = expiredAt;
        this.updatedAt = updatedAt;
    }
}
