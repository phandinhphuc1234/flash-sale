package com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.order.order.domain.model.PurchaseSource;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequestState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Adapter-local record of a resumable Order-owned regular-purchase intake. */
@Entity
@Table(name = "regular_purchase_requests")
public class RegularPurchaseRequestJpaEntity {

    @Id
    private UUID id;
    @Column(name = "shopper_id", nullable = false)
    private UUID shopperId;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PurchaseSource source;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RegularPurchaseRequestState state;
    @Column(name = "proposed_order_id", nullable = false, unique = true)
    private UUID proposedOrderId;
    @Column(name = "proposed_hold_id", nullable = false, unique = true)
    private UUID proposedHoldId;
    @Column(name = "cart_id")
    private UUID cartId;
    @Column(name = "cart_version")
    private Long cartVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_payload", nullable = false, columnDefinition = "jsonb")
    private String snapshotPayload;
    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;
    @Column(name = "order_id", unique = true)
    private UUID orderId;
    @Column(name = "rejection_code", length = 64)
    private String rejectionCode;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rejection_payload", columnDefinition = "jsonb")
    private String rejectionPayload;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;
    @Column(length = 256)
    private String traceparent;
    @Column(length = 512)
    private String tracestate;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RegularPurchaseRequestJpaEntity() {
    }

    public static RegularPurchaseRequestJpaEntity create() {
        return new RegularPurchaseRequestJpaEntity();
    }

    public void apply(UUID id, UUID shopperId, String idempotencyKey, String requestFingerprint,
            PurchaseSource source, RegularPurchaseRequestState state, UUID proposedOrderId,
            UUID proposedHoldId, UUID cartId, Long cartVersion, String snapshotPayload,
            Instant holdExpiresAt, UUID orderId, String rejectionCode, String rejectionPayload,
            String responsePayload, String traceparent, String tracestate, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.shopperId = shopperId;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.source = source;
        this.state = state;
        this.proposedOrderId = proposedOrderId;
        this.proposedHoldId = proposedHoldId;
        this.cartId = cartId;
        this.cartVersion = cartVersion;
        this.snapshotPayload = snapshotPayload;
        this.holdExpiresAt = holdExpiresAt;
        this.orderId = orderId;
        this.rejectionCode = rejectionCode;
        this.rejectionPayload = rejectionPayload;
        this.responsePayload = responsePayload;
        this.traceparent = traceparent;
        this.tracestate = tracestate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getShopperId() { return shopperId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public PurchaseSource getSource() { return source; }
    public RegularPurchaseRequestState getState() { return state; }
    public UUID getProposedOrderId() { return proposedOrderId; }
    public UUID getProposedHoldId() { return proposedHoldId; }
    public UUID getCartId() { return cartId; }
    public Long getCartVersion() { return cartVersion; }
    public String getSnapshotPayload() { return snapshotPayload; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public UUID getOrderId() { return orderId; }
    public String getRejectionCode() { return rejectionCode; }
    public String getTraceparent() { return traceparent; }
    public String getTracestate() { return tracestate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
