package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Allowlisted, signature-verified provider event receipt without raw webhook data. */
@Entity
@Table(name = "payment_provider_event_receipts")
public class PaymentProviderEventReceiptJpaEntity {

    @Id
    private UUID id;
    @Column(name = "provider_event_id", nullable = false, unique = true, length = 255)
    private String providerEventId;
    @Column(name = "provider_event_type", nullable = false, length = 128)
    private String providerEventType;
    @Column(name = "provider_api_version", length = 32)
    private String providerApiVersion;
    @Column(name = "live_mode", nullable = false)
    private boolean liveMode;
    @Column(name = "provider_object_id", length = 255)
    private String providerObjectId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private PaymentJpaEntity payment;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id")
    private PaymentAttemptJpaEntity attempt;
    @Column(name = "order_id")
    private UUID orderId;
    @Column(name = "provider_created_at", nullable = false)
    private Instant providerCreatedAt;
    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;
    @Column(name = "processing_status", nullable = false, length = 24)
    private String processingStatus;
    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;
    @Column(name = "lease_until")
    private Instant leaseUntil;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;
    @Column(name = "processed_at")
    private Instant processedAt;

    protected PaymentProviderEventReceiptJpaEntity() {
    }

    public static PaymentProviderEventReceiptJpaEntity received(UUID id, String providerEventId,
            String providerEventType, String providerApiVersion, boolean liveMode,
            String providerObjectId, UUID orderId, Instant providerCreatedAt, Instant verifiedAt,
            String processingStatus) {
        PaymentProviderEventReceiptJpaEntity entity = new PaymentProviderEventReceiptJpaEntity();
        entity.id = id;
        entity.providerEventId = providerEventId;
        entity.providerEventType = providerEventType;
        entity.providerApiVersion = providerApiVersion;
        entity.liveMode = liveMode;
        entity.providerObjectId = providerObjectId;
        entity.orderId = orderId;
        entity.providerCreatedAt = providerCreatedAt;
        entity.verifiedAt = verifiedAt;
        entity.processingStatus = processingStatus;
        entity.attemptCount = 0;
        entity.nextAttemptAt = verifiedAt;
        return entity;
    }

    public void attachPayment(PaymentJpaEntity payment) {
        this.payment = payment;
    }

    public void attachAttempt(PaymentAttemptJpaEntity attempt) {
        this.attempt = attempt;
    }

    public void markProcessed(Instant processedAt) {
        this.processingStatus = "PROCESSED";
        this.processedAt = processedAt;
        this.leaseOwner = null;
        this.leaseUntil = null;
    }

    public void markIgnored(Instant processedAt) {
        this.processingStatus = "IGNORED";
        this.processedAt = processedAt;
        this.leaseOwner = null;
        this.leaseUntil = null;
    }

    public void reschedule(String errorCode, Instant nextAttemptAt) {
        this.processingStatus = "PENDING";
        this.lastErrorCode = errorCode;
        this.nextAttemptAt = nextAttemptAt;
        this.leaseOwner = null;
        this.leaseUntil = null;
    }

    public void manualReview(String errorCode, Instant observedAt) {
        this.processingStatus = "MANUAL_REVIEW";
        this.lastErrorCode = errorCode;
        this.processedAt = observedAt;
        this.leaseOwner = null;
        this.leaseUntil = null;
    }

    public void claim(String owner, Instant leaseUntil) {
        this.processingStatus = "IN_PROGRESS";
        this.leaseOwner = owner;
        this.leaseUntil = leaseUntil;
        this.attemptCount++;
    }

    public UUID getId() { return id; }
    public String getProviderEventId() { return providerEventId; }
    public String getProviderEventType() { return providerEventType; }
    public String getProviderApiVersion() { return providerApiVersion; }
    public boolean isLiveMode() { return liveMode; }
    public String getProviderObjectId() { return providerObjectId; }
    public PaymentJpaEntity getPayment() { return payment; }
    public PaymentAttemptJpaEntity getAttempt() { return attempt; }
    public UUID getOrderId() { return orderId; }
    public Instant getProviderCreatedAt() { return providerCreatedAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public String getProcessingStatus() { return processingStatus; }
    public String getLeaseOwner() { return leaseOwner; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastErrorCode() { return lastErrorCode; }
    public Instant getProcessedAt() { return processedAt; }
}
