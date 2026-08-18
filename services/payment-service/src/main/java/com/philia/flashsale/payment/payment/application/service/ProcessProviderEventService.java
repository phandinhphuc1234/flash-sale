package com.philia.flashsale.payment.payment.application.service;

import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutResult;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutRetrieveRequest;
import com.philia.flashsale.payment.payment.application.model.provider.ProviderCheckoutState;
import com.philia.flashsale.payment.payment.application.model.webhook.ProviderEventProcessingResult;
import com.philia.flashsale.payment.payment.application.model.webhook.ProviderOutcomeState;
import com.philia.flashsale.payment.payment.application.port.in.ProcessProviderEventUseCase;
import com.philia.flashsale.payment.payment.application.port.out.HostedCheckoutProviderPort;
import com.philia.flashsale.payment.payment.application.port.out.LoadPaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentProviderReceiptPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentTransactionPort;
import com.philia.flashsale.payment.payment.application.port.out.SavePaymentPort;
import com.philia.flashsale.payment.payment.domain.model.Payment;
import com.philia.flashsale.payment.payment.domain.model.PaymentAttempt;
import com.philia.flashsale.payment.payment.domain.model.PaymentAttemptStatus;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import com.philia.flashsale.payment.outbox.application.port.SavePaymentOutboxPort;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Claims verified receipts and converges Payment truth in a short aggregate transaction. */
public final class ProcessProviderEventService implements ProcessProviderEventUseCase {
    private static final String PAYMENT_EVENT_TOPIC = "flashsale.payment.events.v1";

    private final PaymentProviderReceiptPort receipts;
    private final LoadPaymentPort payments;
    private final SavePaymentPort paymentWriter;
    private final SavePaymentOutboxPort outbox;
    private final HostedCheckoutProviderPort provider;
    private final PaymentClockPort clock;
    private final PaymentTransactionPort transactions;
    private final int batchSize;
    private final Duration lease;
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final String leaseOwner = UUID.randomUUID().toString();

    public ProcessProviderEventService(PaymentProviderReceiptPort receipts, LoadPaymentPort payments,
            SavePaymentPort paymentWriter, SavePaymentOutboxPort outbox,
            HostedCheckoutProviderPort provider, PaymentClockPort clock,
            PaymentTransactionPort transactions, int batchSize, Duration lease,
            int maxAttempts, Duration retryBackoff) {
        this.receipts = Objects.requireNonNull(receipts, "receipts");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.paymentWriter = Objects.requireNonNull(paymentWriter, "paymentWriter");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.batchSize = Math.max(1, batchSize);
        this.lease = Objects.requireNonNull(lease, "lease");
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoff = Objects.requireNonNull(retryBackoff, "retryBackoff");
    }

    @Override
    public ProviderEventProcessingResult processBatch() {
        Instant now = clock.now();
        var claimed = receipts.claimReceiptBatch(now, batchSize, leaseOwner, now.plus(lease));
        int processed = 0;
        int deferred = 0;
        int manualReview = 0;
        for (PaymentProviderReceiptPort.Receipt receipt : claimed) {
            ProcessingStatus status = processOne(receipt, now);
            switch (status) {
                case PROCESSED -> processed++;
                case DEFERRED -> deferred++;
                case MANUAL_REVIEW -> manualReview++;
            }
        }
        return new ProviderEventProcessingResult(claimed.size(), processed, deferred, manualReview);
    }

    private ProcessingStatus processOne(PaymentProviderReceiptPort.Receipt receipt, Instant claimedAt) {
        if (receipt.providerObjectId() == null || receipt.providerObjectId().isBlank()) {
            receipts.markReceiptManualReview(receipt.id(), "MISSING_PROVIDER_OBJECT", claimedAt);
            return ProcessingStatus.MANUAL_REVIEW;
        }
        HostedCheckoutResult current;
        try {
            current = provider.retrieve(new HostedCheckoutRetrieveRequest(receipt.providerObjectId()));
        } catch (RuntimeException exception) {
            return deferOrReview(receipt, "PROVIDER_RETRIEVE_ERROR", claimedAt);
        }
        if (current == null || current.state() == ProviderCheckoutState.UNKNOWN) {
            return deferOrReview(receipt, "PROVIDER_STATE_UNKNOWN", claimedAt);
        }
        try {
            return transactions.execute(() -> applyInTransaction(receipt, current));
        } catch (RuntimeException exception) {
            return deferOrReview(receipt, "OUTCOME_PERSISTENCE_ERROR", claimedAt);
        }
    }

    private ProcessingStatus applyInTransaction(PaymentProviderReceiptPort.Receipt receipt,
            HostedCheckoutResult current) {
        Optional<Payment> payment = resolvePayment(receipt);
        if (payment.isEmpty()) {
            receipts.markReceiptManualReview(receipt.id(), "PAYMENT_NOT_FOUND", clock.now());
            return ProcessingStatus.MANUAL_REVIEW;
        }
        Payment aggregate = payments.findLockedById(payment.get().id()).orElse(null);
        if (aggregate == null) {
            receipts.markReceiptManualReview(receipt.id(), "PAYMENT_NOT_FOUND", clock.now());
            return ProcessingStatus.MANUAL_REVIEW;
        }
        PaymentAttempt attempt = resolveAttempt(aggregate, receipt, current.providerSessionId());
        if (attempt == null) {
            receipts.markReceiptManualReview(receipt.id(), "ATTEMPT_NOT_FOUND", clock.now());
            return ProcessingStatus.MANUAL_REVIEW;
        }
        long beforeVersion = aggregate.aggregateVersion();
        PaymentStatus beforeStatus = aggregate.status();
        applyOutcome(aggregate, attempt, current);
        if (aggregate.aggregateVersion() != beforeVersion) {
            paymentWriter.save(aggregate);
            saveOutcomeFact(receipt, aggregate, attempt, current);
        } else if (aggregate.status() != beforeStatus
                || attempt.status() != PaymentAttemptStatus.SUCCEEDED) {
            paymentWriter.save(aggregate);
        }
        receipts.markProcessed(receipt.id(), clock.now());
        return ProcessingStatus.PROCESSED;
    }

    private Optional<Payment> resolvePayment(PaymentProviderReceiptPort.Receipt receipt) {
        if (receipt.paymentId() != null) {
            Optional<Payment> byId = payments.findById(receipt.paymentId());
            if (byId.isPresent()) {
                return byId;
            }
        }
        if (receipt.orderId() != null) {
            Optional<Payment> byOrder = payments.findByOrderId(receipt.orderId());
            if (byOrder.isPresent()) {
                return byOrder;
            }
        }
        return payments.findByProviderSessionId(receipt.providerObjectId());
    }

    private PaymentAttempt resolveAttempt(Payment payment, PaymentProviderReceiptPort.Receipt receipt,
            String providerSessionId) {
        return payment.attempts().stream()
                .filter(candidate -> receipt.attemptId() == null || candidate.id().equals(receipt.attemptId()))
                .filter(candidate -> providerSessionId == null
                        || providerSessionId.equals(candidate.providerSessionId()))
                .findFirst()
                .orElseGet(() -> payment.attempts().stream()
                        .filter(candidate -> receipt.attemptId() != null
                                && candidate.id().equals(receipt.attemptId()))
                        .findFirst().orElse(null));
    }

    private void applyOutcome(Payment payment, PaymentAttempt attempt, HostedCheckoutResult result) {
        Instant observedAt = result.observedAt();
        switch (result.state()) {
            case PAID -> payment.markProviderPaid(attempt.id(), result.providerSessionId(),
                    result.providerPaymentIntentId(), observedAt);
            case EXPIRED -> payment.markProviderExpired(attempt.id(), observedAt);
            case FAILED -> payment.markProviderTerminalFailure(attempt.id(), observedAt);
            case OPEN -> {
                if (attempt.status() == PaymentAttemptStatus.CREATING
                        || attempt.status() == PaymentAttemptStatus.UNKNOWN) {
                    payment.markAttemptOpen(attempt.id(), result.providerSessionId(),
                            result.providerExpiresAt(), observedAt);
                }
            }
            case PROCESSING -> {
                if (attempt.status() != PaymentAttemptStatus.PROCESSING) {
                    payment.markAttemptProcessing(attempt.id(), "processing", observedAt);
                }
            }
            case UNKNOWN -> payment.markAttemptUnknown(attempt.id(), "unknown", observedAt);
        }
    }

    private void saveOutcomeFact(PaymentProviderReceiptPort.Receipt receipt, Payment payment,
            PaymentAttempt attempt, HostedCheckoutResult result) {
        String eventType = payment.status() == PaymentStatus.SUCCEEDED ? "PaymentSucceeded" : "PaymentFailed";
        UUID eventId = UUID.nameUUIDFromBytes(("provider-outcome:" + payment.id() + ":"
                + payment.aggregateVersion() + ":" + eventType).getBytes(StandardCharsets.UTF_8));
        String payload = payment.status() == PaymentStatus.SUCCEEDED
                ? successPayload(eventId, receipt.id(), payment, attempt, result)
                : failurePayload(eventId, receipt.id(), payment, attempt, result);
        outbox.save(new SavePaymentOutboxPort.OutboxRecord(eventId, payment.id(), payment.aggregateVersion(),
                eventType, 1, PAYMENT_EVENT_TOPIC, payment.orderId(), payload,
                null, null, "PENDING", 0, clock.now(), null, null, null, clock.now()));
    }

    private String successPayload(UUID eventId, UUID causationId, Payment payment, PaymentAttempt attempt,
            HostedCheckoutResult result) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"PaymentSucceeded\","
                + "\"eventVersion\":1,\"producer\":\"payment-service\",\"aggregateType\":\"PAYMENT\","
                + "\"aggregateId\":\"" + payment.id() + "\",\"aggregateVersion\":"
                + payment.aggregateVersion() + ",\"correlationId\":\"" + payment.orderId()
                + "\",\"causationId\":\"" + causationId + "\",\"occurredAt\":\""
                + result.observedAt() + "\",\"data\":{\"paymentId\":\"" + payment.id()
                + "\",\"orderId\":\"" + payment.orderId() + "\",\"amount\":"
                + payment.amount().toPlainString() + ",\"currency\":\"" + payment.currency()
                + "\",\"paidAt\":\"" + result.observedAt() + "\",\"provider\":\"STRIPE\","
                + "\"providerSessionId\":\"" + safe(attempt.providerSessionId())
                + "\",\"providerPaymentIntentId\":" + nullable(attempt.providerPaymentIntentId()) + "}}";
    }

    private String failurePayload(UUID eventId, UUID causationId, Payment payment, PaymentAttempt attempt,
            HostedCheckoutResult result) {
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
                + nullable(attempt.providerSessionId()) + "}}";
    }

    private ProcessingStatus deferOrReview(PaymentProviderReceiptPort.Receipt receipt,
            String errorCode, Instant now) {
        if (receipt.attemptCount() >= maxAttempts) {
            receipts.markReceiptManualReview(receipt.id(), errorCode, now);
            return ProcessingStatus.MANUAL_REVIEW;
        }
        receipts.reschedule(receipt.id(), errorCode, now.plus(retryBackoff));
        return ProcessingStatus.DEFERRED;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String nullable(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private enum ProcessingStatus { PROCESSED, DEFERRED, MANUAL_REVIEW }
}
