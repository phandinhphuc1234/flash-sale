package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.payment.payment.domain.model.FailureReason;
import com.philia.flashsale.payment.payment.domain.model.PaymentAttempt;
import com.philia.flashsale.payment.payment.domain.model.PaymentAttemptStatus;
import com.philia.flashsale.payment.payment.domain.model.PaymentProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** JPA representation of a single Payment provider attempt. */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttemptJpaEntity {

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private PaymentJpaEntity payment;
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentAttemptStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentProvider provider;
    @Column(name = "provider_idempotency_key", nullable = false, length = 255, unique = true)
    private String providerIdempotencyKey;
    @Column(name = "provider_session_id", length = 255, unique = true)
    private String providerSessionId;
    @Column(name = "provider_payment_intent_id", length = 255)
    private String providerPaymentIntentId;
    @Column(name = "first_submitted_at")
    private Instant firstSubmittedAt;
    @Column(name = "safe_replay_until")
    private Instant safeReplayUntil;
    @Column(name = "provider_expires_at")
    private Instant providerExpiresAt;
    @Column(name = "last_provider_state", length = 32)
    private String lastProviderState;
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 64)
    private FailureReason failureReason;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentAttemptJpaEntity() {
    }

    public static PaymentAttemptJpaEntity from(PaymentAttempt attempt, PaymentJpaEntity payment) {
        PaymentAttemptJpaEntity entity = new PaymentAttemptJpaEntity();
        entity.id = attempt.id();
        entity.payment = payment;
        entity.updateFrom(attempt);
        entity.rowVersion = 0;
        return entity;
    }

    public void updateFrom(PaymentAttempt attempt) {
        this.id = attempt.id();
        this.attemptNumber = attempt.attemptNumber();
        this.status = attempt.status();
        this.provider = attempt.provider();
        this.providerIdempotencyKey = attempt.providerIdempotencyKey();
        this.providerSessionId = attempt.providerSessionId();
        this.providerPaymentIntentId = attempt.providerPaymentIntentId();
        this.firstSubmittedAt = attempt.firstSubmittedAt();
        this.safeReplayUntil = attempt.safeReplayUntil();
        this.providerExpiresAt = attempt.providerExpiresAt();
        this.lastProviderState = attempt.lastProviderState();
        this.failureReason = attempt.failureReason();
        this.createdAt = attempt.createdAt();
        this.updatedAt = attempt.updatedAt();
    }

    public UUID getId() { return id; }
    public PaymentJpaEntity getPayment() { return payment; }
    public int getAttemptNumber() { return attemptNumber; }
    public PaymentAttemptStatus getStatus() { return status; }
    public PaymentProvider getProvider() { return provider; }
    public String getProviderIdempotencyKey() { return providerIdempotencyKey; }
    public String getProviderSessionId() { return providerSessionId; }
    public String getProviderPaymentIntentId() { return providerPaymentIntentId; }
    public Instant getFirstSubmittedAt() { return firstSubmittedAt; }
    public Instant getSafeReplayUntil() { return safeReplayUntil; }
    public Instant getProviderExpiresAt() { return providerExpiresAt; }
    public String getLastProviderState() { return lastProviderState; }
    public FailureReason getFailureReason() { return failureReason; }
    public long getRowVersion() { return rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
