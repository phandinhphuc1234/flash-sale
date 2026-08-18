package com.philia.flashsale.payment.payment.adapter.in.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.payment.configuration.StripeCheckoutProperties;
import com.philia.flashsale.payment.payment.application.exception.WebhookVerificationException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import com.stripe.net.Webhook;

/** Signature and allowlist boundary tests; fixtures contain no real credentials or card data. */
class StripeWebhookVerifierTests {
    private static final Instant NOW = Instant.parse("2026-08-18T08:00:00Z");
    private static final String SECRET = "whsec_test_only";
    private static final String API_VERSION = "2026-07-29.dahlia";
    private final StripeWebhookVerifier verifier = new StripeWebhookVerifier(properties(),
            new StripeWebhookMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void verifiesExactRawBodyAndExtractsOnlyAllowlistedMetadata() throws Exception {
        byte[] body = payload("checkout.session.completed", "paid").getBytes(StandardCharsets.UTF_8);
        var event = verifier.verify(body, signature(body, SECRET, NOW));

        assertThat(event.providerEventId()).isEqualTo("evt_test_001");
        assertThat(event.providerObjectId()).isEqualTo("cs_test_001");
        assertThat(event.observedOutcome().state())
                .isEqualTo(com.philia.flashsale.payment.payment.application.model.webhook.ProviderOutcomeState.PAID);
        assertThat(event.paymentId()).isNotNull();
    }

    @Test
    void mutatedBodyAndWrongSecretAreRejectedWithoutEchoingSensitiveData() throws Exception {
        byte[] body = payload("checkout.session.completed", "paid").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> verifier.verify((payload("checkout.session.completed", "unpaid")
                .getBytes(StandardCharsets.UTF_8)), signature(body, SECRET, NOW)))
                .isInstanceOf(WebhookVerificationException.class)
                .hasMessageNotContaining(SECRET);
        assertThatThrownBy(() -> verifier.verify(body, signature(body, "wrong_secret", NOW)))
                .isInstanceOf(WebhookVerificationException.class)
                .hasMessage("INVALID_SIGNATURE");
    }

    @Test
    void staleTimestampAndModeOrApiVersionMismatchArePermanentRejections() throws Exception {
        byte[] body = payload("checkout.session.completed", "paid").getBytes(StandardCharsets.UTF_8);
        String stale = signature(body, SECRET, NOW.minus(Duration.ofMinutes(10)));
        assertThatThrownBy(() -> verifier.verify(body, stale))
                .isInstanceOf(WebhookVerificationException.class);

        byte[] live = payload("checkout.session.completed", "paid").replace("\"livemode\":false",
                "\"livemode\":true").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> verifier.verify(live, signature(live, SECRET, NOW)))
                .isInstanceOf(WebhookVerificationException.class)
                .hasMessage("MODE_MISMATCH");

        byte[] wrongVersion = payload("checkout.session.completed", "paid")
                .replace(API_VERSION, "2025-01-01.acacia").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> verifier.verify(wrongVersion, signature(wrongVersion, SECRET, NOW)))
                .isInstanceOf(WebhookVerificationException.class)
                .hasMessage("API_VERSION_MISMATCH");
    }

    @Test
    void unsupportedSignedEventIsSafeToIgnoreAndMalformedPayloadIsRejected() throws Exception {
        byte[] unsupported = payload("charge.succeeded", "paid").getBytes(StandardCharsets.UTF_8);
        var event = verifier.verify(unsupported, signature(unsupported, SECRET, NOW));
        assertThat(event.supported()).isFalse();
        assertThat(event.providerObjectId()).isNull();

        byte[] malformed = "not-json".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> verifier.verify(malformed, signature(malformed, SECRET, NOW)))
                .isInstanceOf(WebhookVerificationException.class);
    }

    private StripeCheckoutProperties properties() {
        return new StripeCheckoutProperties(true, "test", URI.create("https://api.stripe.com"),
                "sk_test_never_used", "pk_test_never_used", SECRET, API_VERSION,
                Duration.ofMinutes(5), Duration.ofSeconds(5), Duration.ofSeconds(20), 2,
                URI.create("https://merchant.test/success"), URI.create("https://merchant.test/cancel"));
    }

    private String payload(String type, String paymentStatus) {
        return "{\"id\":\"evt_test_001\",\"object\":\"event\",\"api_version\":\""
                + API_VERSION + "\",\"created\":1787036400,\"livemode\":false,\"type\":\""
                + type + "\",\"data\":{\"object\":{\"id\":\"cs_test_001\","
                + "\"object\":\"checkout.session\",\"status\":\"complete\","
                + "\"payment_status\":\"" + paymentStatus + "\",\"payment_intent\":\"pi_test_001\","
                + "\"metadata\":{\"paymentId\":\"00000000-0000-0000-0000-000000000001\","
                + "\"attemptId\":\"00000000-0000-0000-0000-000000000002\","
                + "\"orderId\":\"00000000-0000-0000-0000-000000000003\","
                + "\"unsafe\":\"ignored\"}}}}";
    }

    private String signature(byte[] body, String secret, Instant timestamp) throws Exception {
        String payload = new String(body, StandardCharsets.UTF_8);
        String signed = timestamp.getEpochSecond() + "." + payload;
        return "t=" + timestamp.getEpochSecond() + ",v1="
                + Webhook.Util.computeHmacSha256(secret, signed);
    }
}
