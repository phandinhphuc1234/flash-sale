package com.philia.flashsale.payment.outbox.application.service;

import com.philia.flashsale.payment.outbox.application.model.PaymentOutboxEvent;
import com.philia.flashsale.payment.outbox.application.model.PaymentOutboxPublicationResult;
import com.philia.flashsale.payment.outbox.application.port.ClaimPaymentOutboxPort;
import com.philia.flashsale.payment.outbox.application.port.PublishPaymentOutboxPort;
import com.philia.flashsale.payment.outbox.application.port.UpdatePaymentOutboxPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sequentially relays leased snapshots and never changes Payment truth during Kafka I/O. */
public final class PaymentOutboxPublicationService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentOutboxPublicationService.class);
    private final ClaimPaymentOutboxPort claimPort;
    private final UpdatePaymentOutboxPort updatePort;
    private final PublishPaymentOutboxPort publishPort;
    private final PaymentClockPort clock;
    private final Duration claimLease;
    private final int batchSize;
    private final Duration retryBackoffCap;
    private final String workerId;

    public PaymentOutboxPublicationService(ClaimPaymentOutboxPort claimPort,
            UpdatePaymentOutboxPort updatePort, PublishPaymentOutboxPort publishPort,
            PaymentClockPort clock, Duration claimLease, int batchSize, Duration retryBackoffCap,
            String workerId) {
        this.claimPort = Objects.requireNonNull(claimPort, "claimPort");
        this.updatePort = Objects.requireNonNull(updatePort, "updatePort");
        this.publishPort = Objects.requireNonNull(publishPort, "publishPort");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.claimLease = requirePositive(claimLease, "claim lease");
        this.batchSize = requirePositive(batchSize, "batch size");
        this.retryBackoffCap = requirePositive(retryBackoffCap, "retry backoff cap");
        this.workerId = requireText(workerId, "worker id");
    }

    public PaymentOutboxPublicationResult publishDue() {
        Instant now = clock.now();
        List<PaymentOutboxEvent> claimed;
        try {
            claimed = claimPort.claimBatch(now, batchSize, workerId, now.plus(claimLease));
        } catch (RuntimeException exception) {
            LOG.warn("payment_outbox_claim_failed failureType={}", exception.getClass().getSimpleName());
            return new PaymentOutboxPublicationResult(0, 0, 0, 0);
        }

        int published = 0;
        int retried = 0;
        int leaseLost = 0;
        for (PaymentOutboxEvent event : claimed) {
            try {
                publishPort.publish(event);
                if (updatePort.markPublished(event.eventId(), workerId, clock.now())) {
                    published++;
                } else {
                    leaseLost++;
                    LOG.warn("payment_outbox_publish_ack_lease_lost eventId={} eventType={}",
                            event.eventId(), event.eventType());
                }
            } catch (RuntimeException exception) {
                retried++;
                Instant nextAttemptAt = PaymentOutboxRetryPolicy.nextAttemptAt(
                        clock.now(), event.attemptCount(), retryBackoffCap);
                try {
                    updatePort.recordFailure(event.eventId(), workerId, clock.now(),
                            sanitizeFailure(exception), nextAttemptAt);
                } catch (RuntimeException updateException) {
                    LOG.warn("payment_outbox_retry_record_deferred eventId={} failureType={}",
                            event.eventId(), updateException.getClass().getSimpleName());
                }
                LOG.warn("payment_outbox_publish_failed eventId={} eventType={} failureType={}",
                        event.eventId(), event.eventType(), exception.getClass().getSimpleName());
            }
        }
        return new PaymentOutboxPublicationResult(claimed.size(), published, retried, leaseLost);
    }

    private static String sanitizeFailure(RuntimeException exception) {
        String message = exception.getMessage();
        String safe = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        safe = safe.replaceAll("(?i)(bearer\\s+)[^\\s]+", "$1[REDACTED]")
                .replaceAll("(?i)(secret|token|password|key)=([^\\s,;]+)", "$1=[REDACTED]")
                .replaceAll("[\\r\\n\\t]+", " ").trim();
        return safe.length() > 128 ? safe.substring(0, 128) : safe;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
