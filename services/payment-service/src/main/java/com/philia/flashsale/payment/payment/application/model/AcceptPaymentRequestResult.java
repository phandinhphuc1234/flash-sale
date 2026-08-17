package com.philia.flashsale.payment.payment.application.model;

import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import java.util.Objects;
import java.util.UUID;

/** Stable semantic result of accepting or replaying one PaymentRequested command. */
public record AcceptPaymentRequestResult(
        Outcome outcome,
        UUID paymentId,
        UUID outboxEventId,
        String fingerprint,
        PaymentStatus paymentStatus,
        ConflictDetails conflict) {

    public enum Outcome {
        ACCEPTED,
        EXPIRED,
        EVENT_REPLAYED,
        BUSINESS_REPLAYED,
        CONFLICT
    }

    public record ConflictDetails(UUID establishedPaymentId, String establishedFingerprint,
            String incomingFingerprint, String reason) {

        public ConflictDetails {
            Objects.requireNonNull(incomingFingerprint, "incomingFingerprint");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public AcceptPaymentRequestResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(paymentStatus, "paymentStatus");
        if (outcome == Outcome.CONFLICT && conflict == null) {
            throw new IllegalArgumentException("conflict outcome requires conflict details");
        }
        if (outcome != Outcome.CONFLICT && conflict != null) {
            throw new IllegalArgumentException("non-conflict outcome cannot contain conflict details");
        }
        if (outcome == Outcome.EXPIRED && outboxEventId == null) {
            throw new IllegalArgumentException("expired outcome requires outbox identity");
        }
    }

    public static AcceptPaymentRequestResult accepted(UUID paymentId, String fingerprint,
            PaymentStatus paymentStatus) {
        return new AcceptPaymentRequestResult(Outcome.ACCEPTED, paymentId, null, fingerprint,
                paymentStatus, null);
    }

    public static AcceptPaymentRequestResult expired(UUID paymentId, UUID outboxEventId,
            String fingerprint) {
        return new AcceptPaymentRequestResult(Outcome.EXPIRED, paymentId, outboxEventId, fingerprint,
                PaymentStatus.EXPIRED, null);
    }

    public static AcceptPaymentRequestResult eventReplayed(UUID paymentId, String fingerprint,
            PaymentStatus paymentStatus) {
        return new AcceptPaymentRequestResult(Outcome.EVENT_REPLAYED, paymentId, null, fingerprint,
                paymentStatus, null);
    }

    public static AcceptPaymentRequestResult businessReplayed(UUID paymentId, String fingerprint,
            PaymentStatus paymentStatus) {
        return new AcceptPaymentRequestResult(Outcome.BUSINESS_REPLAYED, paymentId, null, fingerprint,
                paymentStatus, null);
    }

    public static AcceptPaymentRequestResult conflict(UUID paymentId, String establishedFingerprint,
            String incomingFingerprint, String reason) {
        return new AcceptPaymentRequestResult(Outcome.CONFLICT, paymentId, null, incomingFingerprint,
                PaymentStatus.PENDING,
                new ConflictDetails(paymentId, establishedFingerprint, incomingFingerprint, reason));
    }
}
