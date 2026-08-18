package com.philia.flashsale.payment.payment.adapter.in.webhook;

import com.philia.flashsale.payment.payment.application.exception.WebhookVerificationException;
import com.philia.flashsale.payment.payment.application.model.webhook.ProviderOutcome;
import com.philia.flashsale.payment.payment.application.model.webhook.ProviderOutcomeState;
import com.philia.flashsale.payment.payment.application.model.webhook.VerifiedProviderEvent;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Extracts only the allowlisted Stripe envelope and Checkout Session facts. */
public final class StripeWebhookMapper {
    public static final String COMPLETED = "checkout.session.completed";
    public static final String EXPIRED = "checkout.session.expired";

    public VerifiedProviderEvent toVerifiedEvent(Event event, Instant verifiedAt) {
        if (event == null || event.getId() == null || event.getType() == null
                || event.getCreated() == null || event.getLivemode() == null) {
            throw new WebhookVerificationException(WebhookVerificationException.Reason.MALFORMED_PAYLOAD);
        }
        if (!isSupported(event.getType())) {
            return VerifiedProviderEvent.ignored(event.getId(), event.getType(), event.getApiVersion(),
                    event.getLivemode(), Instant.ofEpochSecond(event.getCreated()), verifiedAt);
        }
        StripeObject object = event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new WebhookVerificationException(
                        WebhookVerificationException.Reason.MALFORMED_PAYLOAD));
        if (!(object instanceof Session session) || session.getId() == null || session.getId().isBlank()) {
            throw new WebhookVerificationException(WebhookVerificationException.Reason.MALFORMED_PAYLOAD);
        }
        Map<String, String> metadata = session.getMetadata();
        UUID paymentId = parseUuid(metadata, "paymentId");
        UUID attemptId = parseUuid(metadata, "attemptId");
        UUID orderId = parseUuid(metadata, "orderId");
        ProviderOutcomeState state = COMPLETED.equals(event.getType())
                ? stateOf(session)
                : ProviderOutcomeState.EXPIRED;
        ProviderOutcome outcome = new ProviderOutcome(state, session.getId(), session.getPaymentIntent(), verifiedAt);
        return new VerifiedProviderEvent(event.getId(), event.getType(), event.getApiVersion(),
                event.getLivemode(), session.getId(), paymentId, attemptId, orderId,
                Instant.ofEpochSecond(event.getCreated()), verifiedAt, outcome, true);
    }

    public boolean isSupported(String eventType) {
        return COMPLETED.equals(eventType) || EXPIRED.equals(eventType);
    }

    private ProviderOutcomeState stateOf(Session session) {
        if ("paid".equalsIgnoreCase(session.getPaymentStatus())) {
            return ProviderOutcomeState.PAID;
        }
        if ("open".equalsIgnoreCase(session.getStatus())) {
            return ProviderOutcomeState.UNPAID;
        }
        if ("expired".equalsIgnoreCase(session.getStatus())) {
            return ProviderOutcomeState.EXPIRED;
        }
        if ("complete".equalsIgnoreCase(session.getStatus())) {
            return ProviderOutcomeState.UNPAID;
        }
        return ProviderOutcomeState.UNKNOWN;
    }

    private UUID parseUuid(Map<String, String> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        String value = metadata.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
