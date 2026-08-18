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

/** Durable retry/reconciliation work with reclaimable leases. */
@Entity
@Table(name = "payment_recovery_work")
public class PaymentRecoveryWorkJpaEntity {

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private PaymentJpaEntity payment;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id")
    private PaymentAttemptJpaEntity attempt;
    @Column(name = "work_type", nullable = false, length = 32)
    private String workType;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(name = "provider_idempotency_key", length = 255)
    private String providerIdempotencyKey;
    @Column(name = "safe_replay_until")
    private Instant safeReplayUntil;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;
    @Column(name = "lease_until")
    private Instant leaseUntil;
    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentRecoveryWorkJpaEntity() {
    }

    public static PaymentRecoveryWorkJpaEntity pending(UUID id, PaymentJpaEntity payment,
            PaymentAttemptJpaEntity attempt, String workType, String providerIdempotencyKey,
            Instant safeReplayUntil, Instant now) {
        PaymentRecoveryWorkJpaEntity entity = new PaymentRecoveryWorkJpaEntity();
        entity.id = id;
        entity.payment = payment;
        entity.attempt = attempt;
        entity.workType = workType;
        entity.status = "PENDING";
        entity.providerIdempotencyKey = providerIdempotencyKey;
        entity.safeReplayUntil = safeReplayUntil;
        entity.attemptCount = 0;
        entity.nextAttemptAt = now;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void claim(String owner, Instant leaseUntil, Instant now) {
        this.status = "IN_PROGRESS";
        this.leaseOwner = owner;
        this.leaseUntil = leaseUntil;
        this.attemptCount++;
        this.updatedAt = now;
    }

    public void complete(Instant now) {
        this.status = "COMPLETED";
        this.leaseOwner = null;
        this.leaseUntil = null;
        this.updatedAt = now;
    }

    public void manualReview(String errorCode, Instant now) {
        this.status = "MANUAL_REVIEW";
        this.lastErrorCode = errorCode;
        this.leaseOwner = null;
        this.leaseUntil = null;
        this.updatedAt = now;
    }

    public void reschedule(String errorCode, Instant nextAttemptAt, Instant now) {
        this.status = "PENDING";
        this.lastErrorCode = errorCode;
        this.nextAttemptAt = nextAttemptAt;
        this.leaseOwner = null;
        this.leaseUntil = null;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public PaymentJpaEntity getPayment() { return payment; }
    public PaymentAttemptJpaEntity getAttempt() { return attempt; }
    public String getWorkType() { return workType; }
    public String getStatus() { return status; }
    public String getProviderIdempotencyKey() { return providerIdempotencyKey; }
    public Instant getSafeReplayUntil() { return safeReplayUntil; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLeaseOwner() { return leaseOwner; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public String getLastErrorCode() { return lastErrorCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
