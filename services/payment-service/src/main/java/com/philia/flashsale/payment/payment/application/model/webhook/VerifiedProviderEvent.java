package com.philia.flashsale.payment.payment.application.model.webhook;

import java.time.Instant;
import java.util.UUID;

/** Allowlisted, signature-verified Stripe event metadata accepted by the application core. */
public record VerifiedProviderEvent(
        String providerEventId,
        String providerEventType,
        String providerApiVersion,
        boolean liveMode,
        String providerObjectId,
        UUID paymentId,
        UUID attemptId,
        UUID orderId,
        Instant providerCreatedAt,
        Instant verifiedAt,
        ProviderOutcome observedOutcome,
        boolean supported) {

    public VerifiedProviderEvent {
        if (providerEventId == null || providerEventId.isBlank()
                || providerEventType == null || providerEventType.isBlank()
                || providerCreatedAt == null || verifiedAt == null) {
            throw new IllegalArgumentException("verified provider event identity and timestamps are required");
        }
        if (supported && (providerObjectId == null || providerObjectId.isBlank())) {
            throw new IllegalArgumentException("supported provider event requires an object identity");
        }
    }

    public static VerifiedProviderEvent ignored(String eventId, String eventType, String apiVersion,
            boolean liveMode, Instant providerCreatedAt, Instant verifiedAt) {
        return new VerifiedProviderEvent(eventId, eventType, apiVersion, liveMode, null, null, null,
                null, providerCreatedAt, verifiedAt, null, false);
    }
}
