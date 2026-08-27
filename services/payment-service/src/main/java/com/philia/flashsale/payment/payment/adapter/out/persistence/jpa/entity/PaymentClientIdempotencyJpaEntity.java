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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Durable owner-scoped Checkout idempotency record; raw client keys are never stored. */
@Entity
@Table(name = "payment_client_idempotency")
public class PaymentClientIdempotencyJpaEntity {

    @Id
    private UUID id;
    @Column(nullable = false, length = 64)
    private String operation;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "key_digest", nullable = false, length = 64)
    private String keyDigest;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private PaymentJpaEntity payment;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id")
    private PaymentAttemptJpaEntity attempt;
    @Column(name = "outcome_status", nullable = false, length = 24)
    private String outcomeStatus;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentClientIdempotencyJpaEntity() {
    }

    public static PaymentClientIdempotencyJpaEntity create(UUID id, String operation, String keyDigest,
            UUID userId, PaymentJpaEntity payment, String requestFingerprint, Instant createdAt) {
        PaymentClientIdempotencyJpaEntity entity = new PaymentClientIdempotencyJpaEntity();
        entity.id = id;
        entity.operation = operation;
        entity.keyDigest = keyDigest;
        entity.userId = userId;
        entity.payment = payment;
        entity.requestFingerprint = requestFingerprint;
        entity.outcomeStatus = "ACCEPTED";
        entity.createdAt = createdAt;
        entity.updatedAt = createdAt;
        return entity;
    }

    public void attachAttempt(PaymentAttemptJpaEntity attempt, String outcomeStatus, Instant updatedAt) {
        if (this.attempt != null && this.attempt.getId().equals(attempt.getId())
                && this.outcomeStatus.equals(outcomeStatus)) {
            return;
        }
        this.attempt = attempt;
        this.outcomeStatus = outcomeStatus;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getOperation() { return operation; }
    public String getKeyDigest() { return keyDigest; }
    public UUID getUserId() { return userId; }
    public PaymentJpaEntity getPayment() { return payment; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public PaymentAttemptJpaEntity getAttempt() { return attempt; }
    public String getOutcomeStatus() { return outcomeStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
