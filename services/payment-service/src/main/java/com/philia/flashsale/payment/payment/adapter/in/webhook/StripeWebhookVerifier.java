package com.philia.flashsale.payment.payment.adapter.in.webhook;

import com.philia.flashsale.payment.configuration.StripeCheckoutProperties;
import com.philia.flashsale.payment.payment.application.exception.WebhookVerificationException;
import com.philia.flashsale.payment.payment.application.model.webhook.VerifiedProviderEvent;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

/** Stripe official-library signature boundary; raw bytes never leave this adapter. */
public class StripeWebhookVerifier {
    private final StripeCheckoutProperties properties;
    private final StripeWebhookMapper mapper;
    private final Clock clock;

    public StripeWebhookVerifier(StripeCheckoutProperties properties, StripeWebhookMapper mapper) {
        this(properties, mapper, Clock.systemUTC());
    }

    public StripeWebhookVerifier(StripeCheckoutProperties properties, StripeWebhookMapper mapper, Clock clock) {
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    public VerifiedProviderEvent verify(byte[] rawBody, String signature) {
        if (rawBody == null || rawBody.length == 0) {
            throw new WebhookVerificationException(WebhookVerificationException.Reason.MALFORMED_PAYLOAD);
        }
        if (signature == null || signature.isBlank()) {
            throw new WebhookVerificationException(WebhookVerificationException.Reason.MISSING_SIGNATURE);
        }
        String payload = decodeUtf8(rawBody);
        Event event;
        try {
            long toleranceSeconds = Math.max(1L, properties.webhookTolerance().toSeconds());
            event = Webhook.constructEvent(payload, signature, properties.webhookSecret(),
                    toleranceSeconds, clock);
        } catch (com.stripe.exception.SignatureVerificationException exception) {
            throw new WebhookVerificationException(WebhookVerificationException.Reason.INVALID_SIGNATURE);
        } catch (RuntimeException exception) {
            throw new WebhookVerificationException(WebhookVerificationException.Reason.MALFORMED_PAYLOAD);
        }
        if (event.getApiVersion() == null || !properties.expectedApiVersion().equals(event.getApiVersion())) {
            throw new WebhookVerificationException(WebhookVerificationException.Reason.API_VERSION_MISMATCH);
        }
        boolean expectedLive = "live".equalsIgnoreCase(properties.mode());
        if (event.getLivemode() == null || event.getLivemode() != expectedLive) {
            throw new WebhookVerificationException(WebhookVerificationException.Reason.MODE_MISMATCH);
        }
        try {
            return mapper.toVerifiedEvent(event, Instant.now(clock));
        } catch (WebhookVerificationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WebhookVerificationException(WebhookVerificationException.Reason.MALFORMED_PAYLOAD);
        }
    }

    private String decodeUtf8(byte[] rawBody) {
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(rawBody));
            return decoded.toString();
        } catch (CharacterCodingException exception) {
            throw new WebhookVerificationException(WebhookVerificationException.Reason.UNSUPPORTED_ENCODING);
        }
    }
}
