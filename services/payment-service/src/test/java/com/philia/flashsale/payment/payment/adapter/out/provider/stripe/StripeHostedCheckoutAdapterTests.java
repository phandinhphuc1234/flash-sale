package com.philia.flashsale.payment.payment.adapter.out.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.philia.flashsale.payment.configuration.StripeCheckoutProperties;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutCreateRequest;
import com.philia.flashsale.payment.payment.application.model.provider.ProviderCheckoutState;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Contract-level Stripe mapping checks; no live secret or provider call is used. */
class StripeHostedCheckoutAdapterTests {
    private final StripeCheckoutMapper mapper = new StripeCheckoutMapper();

    @Test
    void mapsHostedCardOnlyPaymentWithAutomaticCaptureAndStableOptions() {
        var request = new HostedCheckoutCreateRequest("payment-1", "order-1", "attempt-1",
                new BigDecimal("250000"), "VND", "stable-provider-key",
                Instant.now().plusSeconds(300), "https://merchant.test/success",
                "https://merchant.test/cancel", java.util.Map.of("paymentId", "payment-1"));
        var params = mapper.toCreateParams(request);

        assertThat(params.getMode()).isEqualTo(com.stripe.param.checkout.SessionCreateParams.Mode.PAYMENT);
        assertThat(params.getPaymentMethodTypes()).containsExactly(
                com.stripe.param.checkout.SessionCreateParams.PaymentMethodType.CARD);
        assertThat(params.getLineItems()).hasSize(1);
        assertThat(params.getExpiresAt()).isGreaterThan(Instant.now().plus(Duration.ofMinutes(29)).getEpochSecond());
        assertThat(params.getMetadata()).containsEntry("paymentId", "payment-1");
        assertThat(params.getSuccessUrl()).isEqualTo("https://merchant.test/success");
        assertThat(params.getPaymentIntentData().getCaptureMethod())
                .isEqualTo(com.stripe.param.checkout.SessionCreateParams.PaymentIntentData.CaptureMethod.AUTOMATIC);
    }

    @Test
    void mapsProviderStateWithoutExposingFailureText() {
        var session = new com.stripe.model.checkout.Session();
        session.setId("cs_test_123");
        session.setStatus("open");
        session.setPaymentStatus("unpaid");
        session.setUrl("https://checkout.stripe.test/redacted");
        var result = mapper.toResult(session, Instant.parse("2026-08-17T00:00:00Z"));

        assertThat(result.state()).isEqualTo(ProviderCheckoutState.OPEN);
        assertThat(result.providerSessionId()).isEqualTo("cs_test_123");
        assertThat(result.failureCategory()).isNull();
    }

    @Test
    void classifierNormalizesTimeoutAndInvalidRequest() {
        var classifier = new StripeFailureClassifier();
        assertThat(classifier.classify(new com.stripe.exception.ApiConnectionException("timeout")))
                .isEqualTo(com.philia.flashsale.payment.payment.application.model.provider.ProviderFailureCategory.TIMEOUT);
    }

    @Test
    void retrieveAndExpireRemainProviderNeutral() throws Exception {
        com.stripe.StripeClient client = mock(com.stripe.StripeClient.class);
        com.stripe.service.CheckoutService checkout = mock(com.stripe.service.CheckoutService.class);
        com.stripe.service.checkout.SessionService sessions = mock(com.stripe.service.checkout.SessionService.class);
        var session = new com.stripe.model.checkout.Session();
        session.setId("cs_test_replay");
        session.setStatus("open");
        session.setPaymentStatus("unpaid");
        session.setUrl("https://checkout.test/replay");
        when(client.checkout()).thenReturn(checkout);
        when(checkout.sessions()).thenReturn(sessions);
        when(sessions.retrieve(org.mockito.ArgumentMatchers.eq("cs_test_replay"), any(com.stripe.net.RequestOptions.class)))
                .thenReturn(session);
        when(sessions.expire(org.mockito.ArgumentMatchers.eq("cs_test_replay"), any(com.stripe.net.RequestOptions.class)))
                .thenReturn(session);

        var adapter = new StripeHostedCheckoutAdapter(client, properties(), mapper,
                new StripeFailureClassifier());
        var retrieved = adapter.retrieve(new com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutRetrieveRequest("cs_test_replay"));
        var expired = adapter.expire(new com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutExpireRequest("cs_test_replay"));

        assertThat(retrieved.providerSessionId()).isEqualTo("cs_test_replay");
        assertThat(expired.state()).isEqualTo(ProviderCheckoutState.OPEN);
    }

    private StripeCheckoutProperties properties() {
        return new StripeCheckoutProperties(true, "test", URI.create("https://api.stripe.com"),
                "sk_test_secret", "pk_test_public", "whsec_test", "2026-07-29.dahlia",
                Duration.ofMinutes(5), Duration.ofSeconds(5), Duration.ofSeconds(20), 2,
                URI.create("https://merchant.test/success"), URI.create("https://merchant.test/cancel"));
    }
}
