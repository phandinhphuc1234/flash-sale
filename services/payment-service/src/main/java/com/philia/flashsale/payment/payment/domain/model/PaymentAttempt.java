package com.philia.flashsale.payment.payment.domain.model;

import com.philia.flashsale.payment.payment.domain.exception.PaymentAttemptException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Domain representation of one durable, provider-idempotent Checkout workflow. */
public final class PaymentAttempt {

    private final UUID id;
    private final UUID paymentId;
    private final int attemptNumber;
    private final PaymentProvider provider;
    private final String providerIdempotencyKey;
    private PaymentAttemptStatus status;
    private String providerSessionId;
    private String providerPaymentIntentId;
    private Instant firstSubmittedAt;
    private Instant safeReplayUntil;
    private Instant providerExpiresAt;
    private String lastProviderState;
    private FailureReason failureReason;
    private final Instant createdAt;
    private Instant updatedAt;

    private PaymentAttempt(UUID id, UUID paymentId, int attemptNumber, PaymentProvider provider,
            String providerIdempotencyKey, PaymentAttemptStatus status, String providerSessionId,
            String providerPaymentIntentId, Instant firstSubmittedAt, Instant safeReplayUntil,
            Instant providerExpiresAt, String lastProviderState, FailureReason failureReason,
            Instant createdAt, Instant updatedAt) {
        this.id = require(id, "attempt id");
        this.paymentId = require(paymentId, "payment id");
        if (attemptNumber < 1 || attemptNumber > 3) {
            throw new PaymentAttemptException("attempt number must be between 1 and 3");
        }
        this.attemptNumber = attemptNumber;
        this.provider = Objects.requireNonNull(provider, "provider");
        if (providerIdempotencyKey == null || providerIdempotencyKey.isBlank()) {
            throw new PaymentAttemptException("provider idempotency key must not be blank");
        }
        this.providerIdempotencyKey = providerIdempotencyKey;
        this.status = Objects.requireNonNull(status, "status");
        this.providerSessionId = providerSessionId;
        this.providerPaymentIntentId = providerPaymentIntentId;
        this.firstSubmittedAt = firstSubmittedAt;
        this.safeReplayUntil = safeReplayUntil;
        this.providerExpiresAt = providerExpiresAt;
        this.lastProviderState = lastProviderState;
        this.failureReason = failureReason;
        this.createdAt = require(createdAt, "created at");
        this.updatedAt = require(updatedAt, "updated at");
    }

    public static PaymentAttempt create(UUID id, UUID paymentId, int attemptNumber,
            PaymentProvider provider, String providerIdempotencyKey, Instant createdAt,
            Instant safeReplayUntil) {
        return new PaymentAttempt(id, paymentId, attemptNumber, provider, providerIdempotencyKey,
                PaymentAttemptStatus.CREATING, null, null, null, safeReplayUntil, null, null, null,
                createdAt, createdAt);
    }

    public static PaymentAttempt reconstitute(UUID id, UUID paymentId, int attemptNumber,
            PaymentProvider provider, String providerIdempotencyKey, PaymentAttemptStatus status,
            String providerSessionId, String providerPaymentIntentId, Instant firstSubmittedAt,
            Instant safeReplayUntil, Instant providerExpiresAt, String lastProviderState,
            FailureReason failureReason, Instant createdAt, Instant updatedAt) {
        return new PaymentAttempt(id, paymentId, attemptNumber, provider, providerIdempotencyKey,
                status, providerSessionId, providerPaymentIntentId, firstSubmittedAt,
                safeReplayUntil, providerExpiresAt, lastProviderState, failureReason, createdAt,
                updatedAt);
    }

    public void markSubmitted(Instant submittedAt) {
        this.firstSubmittedAt = require(submittedAt, "submitted at");
        touch(submittedAt);
    }

    public void markOpen(String sessionId, Instant expiresAt, Instant observedAt) {
        requireState(PaymentAttemptStatus.CREATING, PaymentAttemptStatus.UNKNOWN);
        this.providerSessionId = requireText(sessionId, "provider session id");
        this.providerExpiresAt = expiresAt;
        this.status = PaymentAttemptStatus.OPEN;
        touch(observedAt);
    }

    public void markProcessing(String providerState, Instant observedAt) {
        requireState(PaymentAttemptStatus.OPEN, PaymentAttemptStatus.UNKNOWN,
                PaymentAttemptStatus.CREATING);
        this.lastProviderState = providerState;
        this.status = PaymentAttemptStatus.PROCESSING;
        touch(observedAt);
    }

    public void markUnknown(String providerState, Instant observedAt) {
        if (status == PaymentAttemptStatus.SUCCEEDED) {
            return;
        }
        this.lastProviderState = providerState;
        this.status = PaymentAttemptStatus.UNKNOWN;
        touch(observedAt);
    }

    public void markSucceeded(String sessionId, String paymentIntentId, Instant paidAt) {
        this.providerSessionId = sessionId == null ? providerSessionId : sessionId;
        this.providerPaymentIntentId = paymentIntentId;
        this.failureReason = null;
        this.status = PaymentAttemptStatus.SUCCEEDED;
        touch(paidAt);
    }

    public void markFailed(FailureReason reason, Instant observedAt) {
        if (status == PaymentAttemptStatus.SUCCEEDED) {
            return;
        }
        this.failureReason = Objects.requireNonNull(reason, "failure reason");
        this.status = PaymentAttemptStatus.FAILED;
        touch(observedAt);
    }

    public void markExpired(Instant observedAt) {
        if (status == PaymentAttemptStatus.SUCCEEDED) {
            return;
        }
        this.failureReason = FailureReason.PAYMENT_DEADLINE_EXPIRED;
        this.status = PaymentAttemptStatus.EXPIRED;
        touch(observedAt);
    }

    public UUID id() { return id; }
    public UUID paymentId() { return paymentId; }
    public int attemptNumber() { return attemptNumber; }
    public PaymentProvider provider() { return provider; }
    public String providerIdempotencyKey() { return providerIdempotencyKey; }
    public PaymentAttemptStatus status() { return status; }
    public String providerSessionId() { return providerSessionId; }
    public String providerPaymentIntentId() { return providerPaymentIntentId; }
    public Instant firstSubmittedAt() { return firstSubmittedAt; }
    public Instant safeReplayUntil() { return safeReplayUntil; }
    public Instant providerExpiresAt() { return providerExpiresAt; }
    public String lastProviderState() { return lastProviderState; }
    public FailureReason failureReason() { return failureReason; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public boolean isUnresolved() { return status.unresolved(); }

    private void requireState(PaymentAttemptStatus... allowed) {
        for (PaymentAttemptStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new PaymentAttemptException("attempt cannot transition from " + status);
    }

    private void touch(Instant timestamp) {
        this.updatedAt = require(timestamp, "updated at");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new PaymentAttemptException(label + " must not be blank");
        }
        return value;
    }

    private static <T> T require(T value, String label) {
        return Objects.requireNonNull(value, label);
    }
}
