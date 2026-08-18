package com.philia.flashsale.payment.payment.application.service;

import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutCreateRequest;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutExpireRequest;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutResult;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutRetrieveRequest;
import com.philia.flashsale.payment.payment.application.model.provider.ProviderCheckoutState;
import com.philia.flashsale.payment.payment.application.model.recovery.ReconcilePaymentCommand;
import com.philia.flashsale.payment.payment.application.model.recovery.ReconcilePaymentResult;
import com.philia.flashsale.payment.payment.application.model.recovery.RecoveryBatchResult;
import com.philia.flashsale.payment.payment.application.model.recovery.RecoveryWorkType;
import com.philia.flashsale.payment.payment.application.port.in.ReconcilePaymentUseCase;
import com.philia.flashsale.payment.payment.application.port.out.HostedCheckoutProviderPort;
import com.philia.flashsale.payment.payment.application.port.out.LoadDuePaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.LoadPaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentIdentityPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentRecoveryWorkPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentTransactionPort;
import com.philia.flashsale.payment.payment.application.port.out.SavePaymentPort;
import com.philia.flashsale.payment.payment.domain.model.Payment;
import com.philia.flashsale.payment.payment.domain.model.PaymentAttempt;
import com.philia.flashsale.payment.payment.domain.model.PaymentAttemptStatus;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import com.philia.flashsale.payment.outbox.application.port.SavePaymentOutboxPort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Claim-call-converge reconciliation orchestration. Provider I/O is deliberately outside the
 * local transaction; only the normalized result and the Payment/outbox transition are transactional.
 */
public final class ReconcilePaymentService implements ReconcilePaymentUseCase {

    public static final String PAYMENT_EVENT_TOPIC = "flashsale.payment.events.v1";
    private static final String CREATE_WORK = "CREATE_SESSION";
    private static final String REFRESH_WORK = "REFRESH_SESSION";
    private static final String EXPIRE_WORK = "EXPIRE_SESSION";
    private static final String DEFAULT_SUCCESS_URL = "http://localhost:3000/payments/success";
    private static final String DEFAULT_CANCEL_URL = "http://localhost:3000/payments/cancel";

    private final PaymentRecoveryWorkPort recovery;
    private final LoadPaymentPort payments;
    private final SavePaymentPort paymentWriter;
    private final HostedCheckoutProviderPort provider;
    private final PaymentClockPort clock;
    private final PaymentTransactionPort transactions;
    private final SavePaymentOutboxPort outbox;
    private final LoadDuePaymentPort duePayments;
    private final PaymentIdentityPort identities;
    private final PaymentRecoveryPolicy policy;
    private final int batchSize;
    private final java.time.Duration claimLease;
    private final String successUrl;
    private final String cancelUrl;
    private final String leaseOwner = UUID.randomUUID().toString();

    public ReconcilePaymentService(PaymentRecoveryWorkPort recovery, LoadPaymentPort payments,
            SavePaymentPort paymentWriter, HostedCheckoutProviderPort provider, PaymentClockPort clock,
            PaymentTransactionPort transactions, SavePaymentOutboxPort outbox,
            PaymentRecoveryPolicy policy) {
        this(recovery, payments, paymentWriter, provider, clock, transactions, outbox,
                (now, size) -> List.of(), UUID::randomUUID, policy, 100,
                java.time.Duration.ofSeconds(30), DEFAULT_SUCCESS_URL, DEFAULT_CANCEL_URL);
    }

    public ReconcilePaymentService(PaymentRecoveryWorkPort recovery, LoadPaymentPort payments,
            SavePaymentPort paymentWriter, HostedCheckoutProviderPort provider, PaymentClockPort clock,
            PaymentTransactionPort transactions, SavePaymentOutboxPort outbox, LoadDuePaymentPort duePayments,
            PaymentIdentityPort identities, PaymentRecoveryPolicy policy, int batchSize,
            java.time.Duration claimLease, String successUrl, String cancelUrl) {
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.paymentWriter = Objects.requireNonNull(paymentWriter, "paymentWriter");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.duePayments = Objects.requireNonNull(duePayments, "duePayments");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.policy = Objects.requireNonNull(policy, "policy");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
        this.claimLease = requirePositive(claimLease, "claim lease");
        this.successUrl = requireText(successUrl, "success URL");
        this.cancelUrl = requireText(cancelUrl, "cancel URL");
    }

    @Override
    public RecoveryBatchResult reconcileDueWork() {
        Instant now = clock.now();
        List<PaymentRecoveryWorkPort.Work> claimed = recovery.claimWorkBatch(
                now, batchSize, leaseOwner, now.plus(claimLease));
        int converged = 0;
        int deferred = 0;
        int manualReview = 0;
        for (PaymentRecoveryWorkPort.Work work : claimed) {
            ReconcilePaymentResult result = reconcile(new ReconcilePaymentCommand(work, now));
            switch (result.outcome()) {
                case CONVERGED, NO_OP -> converged++;
                case DEFERRED -> deferred++;
                case MANUAL_REVIEW -> manualReview++;
            }
        }
        return new RecoveryBatchResult(claimed.size(), converged, deferred, manualReview);
    }

    @Override
    public int scheduleDueDeadlines() {
        Instant now = clock.now();
        int scheduled = 0;
        for (Payment payment : duePayments.findDeadlineCandidates(now, batchSize)) {
            if (payment.status() == PaymentStatus.SUCCEEDED || payment.status() == PaymentStatus.FAILED
                    || payment.status() == PaymentStatus.EXPIRED) {
                continue;
            }
            PaymentAttempt attempt = payment.activeAttempt();
            PaymentRecoveryWorkPort.Work work = new PaymentRecoveryWorkPort.Work(
                    identities.newId(), payment.id(), attempt == null ? null : attempt.id(), EXPIRE_WORK,
                    "PENDING", attempt == null ? null : attempt.providerIdempotencyKey(),
                    attempt == null ? null : attempt.safeReplayUntil(), 0, now, null, null, null, now, now);
            recovery.schedule(work);
            scheduled++;
        }
        return scheduled;
    }

    @Override
    public ReconcilePaymentResult reconcile(ReconcilePaymentCommand command) {
        PaymentRecoveryWorkPort.Work work = command.work();
        Instant observedAt = command.observedAt();
        RecoveryWorkType type = RecoveryWorkType.from(work.workType());
        if (type == null) {
            return manualReview(work, "UNKNOWN_WORK_TYPE", observedAt);
        }
        Payment payment = payments.findById(work.paymentId()).orElse(null);
        if (payment == null) {
            return manualReview(work, "PAYMENT_NOT_FOUND", observedAt);
        }
        PaymentAttempt attempt = resolveAttempt(payment, work.attemptId());
        if (attempt == null) {
            if (type == RecoveryWorkType.EXPIRE_SESSION) {
                return expirePaymentWithoutAttempt(work, observedAt);
            }
            return manualReview(work, "ATTEMPT_NOT_FOUND", observedAt);
        }
        if (type == RecoveryWorkType.CREATE_SESSION
                && attempt.providerSessionId() == null
                && !policy.withinSafeReplayWindow(observedAt, work.safeReplayUntil())) {
            return manualReview(work, "CREATE_REPLAY_WINDOW_EXPIRED", observedAt);
        }

        HostedCheckoutResult result;
        try {
            result = callProvider(type, payment, attempt, observedAt);
        } catch (RuntimeException exception) {
            return deferOrReview(work, "PROVIDER_CALL_ERROR", observedAt);
        }
        if (result == null || result.state() == ProviderCheckoutState.UNKNOWN) {
            return deferOrReview(work, "PROVIDER_STATE_UNKNOWN", observedAt);
        }
        if (type == RecoveryWorkType.EXPIRE_SESSION
                && (result.state() == ProviderCheckoutState.OPEN
                || result.state() == ProviderCheckoutState.PROCESSING)) {
            try {
                result = provider.retrieve(new HostedCheckoutRetrieveRequest(attempt.providerSessionId()));
            } catch (RuntimeException exception) {
                return deferOrReview(work, "PROVIDER_RETRIEVE_ERROR", observedAt);
            }
            if (result == null || result.state() == ProviderCheckoutState.UNKNOWN
                    || result.state() == ProviderCheckoutState.OPEN
                    || result.state() == ProviderCheckoutState.PROCESSING) {
                return deferOrReview(work, "SESSION_NOT_CONFIRMED_EXPIRED", observedAt);
            }
        }
        return applyProviderResult(work, result);
    }

    private HostedCheckoutResult callProvider(RecoveryWorkType type, Payment payment,
            PaymentAttempt attempt, Instant observedAt) {
        return switch (type) {
            case CREATE_SESSION -> attempt.providerSessionId() == null
                    ? provider.create(createRequest(payment, attempt))
                    : provider.retrieve(new HostedCheckoutRetrieveRequest(attempt.providerSessionId()));
            case REFRESH_SESSION -> retrieve(attempt);
            case EXPIRE_SESSION -> retrieveOrExpire(attempt, payment, observedAt);
        };
    }

    private HostedCheckoutResult retrieve(PaymentAttempt attempt) {
        if (attempt.providerSessionId() == null) {
            throw new IllegalStateException("provider session is not correlated");
        }
        return provider.retrieve(new HostedCheckoutRetrieveRequest(attempt.providerSessionId()));
    }

    private HostedCheckoutResult retrieveOrExpire(PaymentAttempt attempt, Payment payment,
            Instant observedAt) {
        if (attempt.providerSessionId() == null) {
            // There is no provider object to expire. At the internal deadline this is a safe
            // unpaid outcome, while a missing create response remains bounded by replay policy.
            if (policy.withinSafeReplayWindow(observedAt, attempt.safeReplayUntil())) {
                return provider.create(createRequest(payment, attempt));
            }
            return HostedCheckoutResult.unknown(
                    com.philia.flashsale.payment.payment.application.model.provider.ProviderFailureCategory.NOT_FOUND,
                    observedAt);
        }
        return provider.expire(new HostedCheckoutExpireRequest(attempt.providerSessionId()));
    }

    private ReconcilePaymentResult applyProviderResult(PaymentRecoveryWorkPort.Work work,
            HostedCheckoutResult result) {
        try {
            return transactions.execute(() -> applyInTransaction(work, result));
        } catch (RuntimeException exception) {
            return deferOrReview(work, "OUTCOME_PERSISTENCE_ERROR", clock.now());
        }
    }

    private ReconcilePaymentResult applyInTransaction(PaymentRecoveryWorkPort.Work work,
            HostedCheckoutResult result) {
        Instant now = result.observedAt();
        Payment payment = payments.findLockedById(work.paymentId()).orElse(null);
        if (payment == null) {
            return manualReview(work, "PAYMENT_NOT_FOUND", now);
        }
        PaymentAttempt attempt = resolveAttempt(payment, work.attemptId());
        if (attempt == null) {
            return manualReview(work, "ATTEMPT_NOT_FOUND", now);
        }
        long beforeVersion = payment.aggregateVersion();
        PaymentStatus beforeStatus = payment.status();
        applyResult(payment, attempt, result);
        if (payment.aggregateVersion() != beforeVersion) {
            paymentWriter.save(payment);
            saveOutcomeFact(work.id(), payment, attempt, result);
        } else if (payment.status() != beforeStatus || attempt.status() != PaymentAttemptStatus.SUCCEEDED) {
            paymentWriter.save(payment);
        }
        recovery.complete(work.id(), now);
        ReconcilePaymentResult.Outcome outcome = result.state() == ProviderCheckoutState.UNKNOWN
                ? ReconcilePaymentResult.Outcome.NO_OP : ReconcilePaymentResult.Outcome.CONVERGED;
        return result(work, outcome, result.state().name(), now);
    }

    private void applyResult(Payment payment, PaymentAttempt attempt, HostedCheckoutResult result) {
        switch (result.state()) {
            case PAID -> payment.markProviderPaid(attempt.id(), result.providerSessionId(),
                    result.providerPaymentIntentId(), result.observedAt());
            case EXPIRED -> payment.markProviderExpired(attempt.id(), result.observedAt());
            case FAILED -> payment.markProviderTerminalFailure(attempt.id(), result.observedAt());
            case OPEN -> {
                if (attempt.status() == PaymentAttemptStatus.CREATING
                        || attempt.status() == PaymentAttemptStatus.UNKNOWN) {
                    payment.markAttemptOpen(attempt.id(), result.providerSessionId(),
                            result.providerExpiresAt(), result.observedAt());
                }
            }
            case PROCESSING -> {
                if (attempt.status() != PaymentAttemptStatus.PROCESSING) {
                    payment.markAttemptProcessing(attempt.id(), "processing", result.observedAt());
                }
            }
            case UNKNOWN -> payment.markAttemptUnknown(attempt.id(), "unknown", result.observedAt());
        }
    }

    private ReconcilePaymentResult deferOrReview(PaymentRecoveryWorkPort.Work work,
            String reason, Instant now) {
        int attemptCount = Math.max(1, work.attemptCount());
        if (policy.shouldEscalateToManualReview(attemptCount)) {
            return manualReview(work, reason, now);
        }
        recovery.reschedule(work.id(), reason, policy.nextAttemptAt(now, attemptCount), now);
        return result(work, ReconcilePaymentResult.Outcome.DEFERRED, reason, now);
    }

    private ReconcilePaymentResult expirePaymentWithoutAttempt(PaymentRecoveryWorkPort.Work work,
            Instant now) {
        try {
            return transactions.execute(() -> {
                Payment payment = payments.findLockedById(work.paymentId()).orElse(null);
                if (payment == null) {
                    return manualReview(work, "PAYMENT_NOT_FOUND", now);
                }
                long beforeVersion = payment.aggregateVersion();
                payment.expire(now);
                if (payment.aggregateVersion() != beforeVersion) {
                    paymentWriter.save(payment);
                    saveOutcomeFact(work.id(), payment, null, HostedCheckoutResult.unknown(null, now));
                }
                recovery.complete(work.id(), now);
                return result(work, ReconcilePaymentResult.Outcome.CONVERGED,
                        "PAYMENT_DEADLINE_EXPIRED", now);
            });
        } catch (RuntimeException exception) {
            return deferOrReview(work, "OUTCOME_PERSISTENCE_ERROR", now);
        }
    }

    private ReconcilePaymentResult manualReview(PaymentRecoveryWorkPort.Work work,
            String reason, Instant now) {
        recovery.markManualReview(work.id(), reason, now);
        return result(work, ReconcilePaymentResult.Outcome.MANUAL_REVIEW, reason, now);
    }

    private ReconcilePaymentResult result(PaymentRecoveryWorkPort.Work work,
            ReconcilePaymentResult.Outcome outcome, String reason, Instant now) {
        return new ReconcilePaymentResult(work.id(), work.paymentId(), work.attemptId(), outcome,
                reason, now);
    }

    private PaymentAttempt resolveAttempt(Payment payment, UUID attemptId) {
        if (attemptId != null) {
            return payment.attempts().stream().filter(item -> item.id().equals(attemptId)).findFirst().orElse(null);
        }
        return payment.activeAttempt();
    }

    private HostedCheckoutCreateRequest createRequest(Payment payment, PaymentAttempt attempt) {
        return new HostedCheckoutCreateRequest(payment.id().toString(), payment.orderId().toString(),
                attempt.id().toString(), payment.amount(), payment.currency(), attempt.providerIdempotencyKey(),
                payment.paymentDeadline(), successUrl, cancelUrl,
                Map.of("orderId", payment.orderId().toString(), "paymentId", payment.id().toString(),
                        "attemptId", attempt.id().toString()));
    }

    private void saveOutcomeFact(UUID causationId, Payment payment, PaymentAttempt attempt,
            HostedCheckoutResult result) {
        String eventType = payment.status() == PaymentStatus.SUCCEEDED ? "PaymentSucceeded" : "PaymentFailed";
        UUID eventId = UUID.nameUUIDFromBytes(("provider-outcome:" + payment.id() + ":"
                + payment.aggregateVersion() + ":" + eventType).getBytes(StandardCharsets.UTF_8));
        String payload = payment.status() == PaymentStatus.SUCCEEDED
                ? successPayload(eventId, causationId, payment, attempt, result)
                : failurePayload(eventId, causationId, payment, attempt, result);
        outbox.save(new SavePaymentOutboxPort.OutboxRecord(eventId, payment.id(), payment.aggregateVersion(),
                eventType, 1, PAYMENT_EVENT_TOPIC, payment.orderId(), payload, null, null, "PENDING", 0,
                result.observedAt(), null, null, null, result.observedAt()));
    }

    private String successPayload(UUID eventId, UUID causationId, Payment payment,
            PaymentAttempt attempt, HostedCheckoutResult result) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"PaymentSucceeded\","
                + "\"eventVersion\":1,\"producer\":\"payment-service\",\"aggregateType\":\"PAYMENT\","
                + "\"aggregateId\":\"" + payment.id() + "\",\"aggregateVersion\":"
                + payment.aggregateVersion() + ",\"correlationId\":\"" + payment.orderId()
                + "\",\"causationId\":\"" + causationId + "\",\"occurredAt\":\""
                + result.observedAt() + "\",\"data\":{\"paymentId\":\"" + payment.id()
                + "\",\"orderId\":\"" + payment.orderId() + "\",\"amount\":"
                + payment.amount().toPlainString() + ",\"currency\":\"" + payment.currency()
                + "\",\"paidAt\":\"" + result.observedAt() + "\",\"provider\":\"STRIPE\","
                + "\"providerSessionId\":\"" + safe(attempt == null ? null : attempt.providerSessionId())
                + "\",\"providerPaymentIntentId\":"
                + nullable(attempt == null ? null : attempt.providerPaymentIntentId()) + "}}";
    }

    private String failurePayload(UUID eventId, UUID causationId, Payment payment,
            PaymentAttempt attempt, HostedCheckoutResult result) {
        String reason = payment.failureReason() == null ? "PROVIDER_TERMINAL_FAILURE"
                : payment.failureReason().name();
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"PaymentFailed\","
                + "\"eventVersion\":1,\"producer\":\"payment-service\",\"aggregateType\":\"PAYMENT\","
                + "\"aggregateId\":\"" + payment.id() + "\",\"aggregateVersion\":"
                + payment.aggregateVersion() + ",\"correlationId\":\"" + payment.orderId()
                + "\",\"causationId\":\"" + causationId + "\",\"occurredAt\":\""
                + result.observedAt() + "\",\"data\":{\"paymentId\":\"" + payment.id()
                + "\",\"orderId\":\"" + payment.orderId() + "\",\"amount\":"
                + payment.amount().toPlainString() + ",\"currency\":\"" + payment.currency()
                + "\",\"failedAt\":\"" + result.observedAt() + "\",\"reason\":\""
                + reason + "\",\"provider\":\"STRIPE\",\"providerSessionId\":"
                + nullable(attempt == null ? null : attempt.providerSessionId()) + "}}";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String nullable(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static java.time.Duration requirePositive(java.time.Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
