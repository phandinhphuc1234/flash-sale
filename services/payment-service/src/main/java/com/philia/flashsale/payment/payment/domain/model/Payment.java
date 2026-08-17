package com.philia.flashsale.payment.payment.domain.model;

import com.philia.flashsale.payment.payment.domain.exception.InvalidPaymentException;
import com.philia.flashsale.payment.payment.domain.exception.PaymentAttemptException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Payment aggregate protecting immutable Order snapshots and Checkout attempt invariants. */
public final class Payment {

    public static final int MAX_ATTEMPTS = 3;

    private final UUID id;
    private final UUID orderId;
    private final UUID userId;
    private final Money money;
    private final Instant paymentDeadline;
    private PaymentStatus status;
    private FailureReason failureReason;
    private Instant succeededAt;
    private long aggregateVersion;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<PaymentAttempt> attempts;

    private Payment(UUID id, UUID orderId, UUID userId, Money money, Instant paymentDeadline,
            PaymentStatus status, FailureReason failureReason, Instant succeededAt,
            long aggregateVersion, Instant createdAt, Instant updatedAt, List<PaymentAttempt> attempts) {
        this.id = require(id, "payment id");
        this.orderId = require(orderId, "order id");
        this.userId = require(userId, "user id");
        this.money = Objects.requireNonNull(money, "money");
        this.paymentDeadline = require(paymentDeadline, "payment deadline");
        this.status = Objects.requireNonNull(status, "status");
        this.failureReason = failureReason;
        this.succeededAt = succeededAt;
        if (aggregateVersion < 0) {
            throw new InvalidPaymentException("aggregate version must not be negative");
        }
        this.aggregateVersion = aggregateVersion;
        this.createdAt = require(createdAt, "created at");
        this.updatedAt = require(updatedAt, "updated at");
        this.attempts = new ArrayList<>(attempts == null ? List.of() : attempts);
        validateAttempts();
    }

    public static Payment create(UUID id, UUID orderId, UUID userId, Money money,
            Instant paymentDeadline, Instant createdAt) {
        return new Payment(id, orderId, userId, money, paymentDeadline, PaymentStatus.PENDING,
                null, null, 0, createdAt, createdAt, List.of());
    }

    public static Payment create(UUID id, UUID orderId, UUID userId, BigDecimal amount,
            String currency, Instant paymentDeadline, Instant createdAt) {
        return create(id, orderId, userId, Money.of(amount, currency), paymentDeadline, createdAt);
    }

    public static Payment reconstitute(UUID id, UUID orderId, UUID userId, Money money,
            Instant paymentDeadline, PaymentStatus status, FailureReason failureReason,
            Instant succeededAt, long aggregateVersion, Instant createdAt, Instant updatedAt,
            List<PaymentAttempt> attempts) {
        return new Payment(id, orderId, userId, money, paymentDeadline, status, failureReason,
                succeededAt, aggregateVersion, createdAt, updatedAt, attempts);
    }

    public PaymentAttempt allocateAttempt(UUID attemptId, String providerIdempotencyKey,
            PaymentProvider provider, Instant now, Instant safeReplayUntil) {
        requireNow(now);
        if (status == PaymentStatus.SUCCEEDED || status == PaymentStatus.FAILED
                || status == PaymentStatus.EXPIRED) {
            throw new PaymentAttemptException("payment is not payable in status " + status);
        }
        if (!now.isBefore(paymentDeadline)) {
            expire(now);
            throw new PaymentAttemptException("payment deadline has passed");
        }
        if (attempts.size() >= MAX_ATTEMPTS) {
            throw new PaymentAttemptException("checkout attempt limit reached");
        }
        if (attempts.stream().anyMatch(PaymentAttempt::isUnresolved)) {
            throw new PaymentAttemptException("another Checkout attempt is unresolved");
        }
        int attemptNumber = attempts.stream().mapToInt(PaymentAttempt::attemptNumber).max().orElse(0) + 1;
        PaymentAttempt attempt = PaymentAttempt.create(attemptId, id, attemptNumber, provider,
                providerIdempotencyKey, now, safeReplayUntil);
        attempts.add(attempt);
        status = PaymentStatus.PROCESSING;
        touch(now);
        return attempt;
    }

    public PaymentAttempt allocateAttempt(UUID attemptId, String providerIdempotencyKey,
            Instant now, Instant safeReplayUntil) {
        return allocateAttempt(attemptId, providerIdempotencyKey, PaymentProvider.STRIPE, now,
                safeReplayUntil);
    }

    public void markAttemptUnknown(UUID attemptId, String providerState, Instant now) {
        PaymentAttempt attempt = attempt(attemptId);
        if (attempt.status() == PaymentAttemptStatus.SUCCEEDED || status == PaymentStatus.SUCCEEDED) {
            return;
        }
        attempt.markUnknown(providerState, now);
        status = PaymentStatus.UNKNOWN;
        touch(now);
    }

    public void markAttemptOpen(UUID attemptId, String sessionId, Instant expiresAt, Instant now) {
        attempt(attemptId).markOpen(sessionId, expiresAt, now);
        status = PaymentStatus.PROCESSING;
        touch(now);
    }

    public void markProviderPaid(UUID attemptId, String sessionId, String paymentIntentId,
            Instant paidAt) {
        PaymentAttempt attempt = attempt(attemptId);
        attempt.markSucceeded(sessionId, paymentIntentId, paidAt);
        status = PaymentStatus.SUCCEEDED;
        failureReason = null;
        succeededAt = paidAt;
        incrementVersion(paidAt);
    }

    public void markProviderTerminalFailure(UUID attemptId, Instant now) {
        PaymentAttempt attempt = attempt(attemptId);
        if (attempt.status() == PaymentAttemptStatus.SUCCEEDED || status == PaymentStatus.SUCCEEDED) {
            return;
        }
        attempt.markFailed(FailureReason.PROVIDER_TERMINAL_FAILURE, now);
        if (now.isBefore(paymentDeadline) && attempts.size() < MAX_ATTEMPTS) {
            status = PaymentStatus.PENDING;
        } else {
            status = PaymentStatus.FAILED;
            failureReason = attempts.size() >= MAX_ATTEMPTS
                    ? FailureReason.CHECKOUT_ATTEMPT_LIMIT_REACHED
                    : FailureReason.PROVIDER_TERMINAL_FAILURE;
            incrementVersion(now);
            return;
        }
        touch(now);
    }

    public void expire(Instant now) {
        requireNow(now);
        if (status == PaymentStatus.SUCCEEDED || status == PaymentStatus.FAILED) {
            return;
        }
        attempts.stream().filter(PaymentAttempt::isUnresolved).forEach(attempt -> attempt.markExpired(now));
        status = PaymentStatus.EXPIRED;
        failureReason = FailureReason.PAYMENT_DEADLINE_EXPIRED;
        incrementVersion(now);
    }

    public UUID id() { return id; }
    public UUID orderId() { return orderId; }
    public UUID userId() { return userId; }
    public Money money() { return money; }
    public BigDecimal amount() { return money.amount(); }
    public String currency() { return money.currency(); }
    public Instant paymentDeadline() { return paymentDeadline; }
    public PaymentStatus status() { return status; }
    public FailureReason failureReason() { return failureReason; }
    public Instant succeededAt() { return succeededAt; }
    public long aggregateVersion() { return aggregateVersion; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public List<PaymentAttempt> attempts() { return List.copyOf(attempts); }
    public int attemptsUsed() { return attempts.size(); }
    public boolean hasUnresolvedAttempt() { return attempts.stream().anyMatch(PaymentAttempt::isUnresolved); }
    public PaymentAttempt activeAttempt() {
        return attempts.stream().filter(PaymentAttempt::isUnresolved).findFirst().orElse(null);
    }

    private PaymentAttempt attempt(UUID attemptId) {
        return attempts.stream().filter(candidate -> candidate.id().equals(attemptId)).findFirst()
                .orElseThrow(() -> new PaymentAttemptException("unknown payment attempt"));
    }

    private void incrementVersion(Instant now) {
        aggregateVersion++;
        touch(now);
    }

    private void touch(Instant now) {
        updatedAt = require(now, "updated at");
    }

    private void requireNow(Instant now) {
        require(now, "current time");
    }

    private void validateAttempts() {
        if (attempts.size() > MAX_ATTEMPTS) {
            throw new InvalidPaymentException("payment cannot have more than three attempts");
        }
        long unresolved = attempts.stream().filter(PaymentAttempt::isUnresolved).count();
        if (unresolved > 1) {
            throw new InvalidPaymentException("payment cannot have more than one unresolved attempt");
        }
        if (attempts.stream().map(PaymentAttempt::paymentId).anyMatch(candidate -> !id.equals(candidate))) {
            throw new InvalidPaymentException("attempt belongs to another payment");
        }
    }

    private static <T> T require(T value, String label) {
        return Objects.requireNonNull(value, label);
    }
}
