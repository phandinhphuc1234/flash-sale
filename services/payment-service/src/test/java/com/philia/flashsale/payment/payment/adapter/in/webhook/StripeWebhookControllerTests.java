package com.philia.flashsale.payment.payment.adapter.in.webhook;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.payment.payment.application.exception.ProviderReceiptUnavailableException;
import com.philia.flashsale.payment.payment.application.exception.WebhookVerificationException;
import com.philia.flashsale.payment.payment.application.model.webhook.ProviderEventAcceptanceResult;
import com.philia.flashsale.payment.payment.application.model.webhook.VerifiedProviderEvent;
import com.philia.flashsale.payment.payment.application.port.in.AcceptProviderEventUseCase;
import com.philia.flashsale.payment.configuration.StripeCheckoutProperties;
import java.time.Instant;
import java.time.Duration;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;

/** Stripe protocol responses stay empty and never use the repository JSON error envelope. */
class StripeWebhookControllerTests {
    private MockMvc mvc;
    private StubVerifier verifier;
    private AcceptProviderEventUseCase acceptProviderEvent;
    private RuntimeException verifierFailure;
    private RuntimeException acceptanceFailure;
    private VerifiedProviderEvent verifiedEvent;
    private ProviderEventAcceptanceResult acceptanceResult;

    @BeforeEach
    void setUp() {
        verifierFailure = null;
        acceptanceFailure = null;
        verifiedEvent = null;
        acceptanceResult = null;
        verifier = new StubVerifier();
        acceptProviderEvent = event -> {
            if (acceptanceFailure != null) {
                throw acceptanceFailure;
            }
            return acceptanceResult;
        };
        mvc = MockMvcBuilders.standaloneSetup(new StripeWebhookController(verifier, acceptProviderEvent)).build();
    }

    @Test
    void durableAcceptedAndDuplicateReceiptsBothReturnEmpty204() throws Exception {
        var event = event(true);
        verifiedEvent = event;
        acceptanceResult = new ProviderEventAcceptanceResult(
                ProviderEventAcceptanceResult.Status.ACCEPTED, UUID.randomUUID());
        mvc.perform(post("/webhooks/v1/payments/stripe").header("Stripe-Signature", "sig")
                        .contentType("application/json").content("{\"signed\":true}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        acceptanceResult = new ProviderEventAcceptanceResult(
                ProviderEventAcceptanceResult.Status.DUPLICATE, UUID.randomUUID());
        mvc.perform(post("/webhooks/v1/payments/stripe").header("Stripe-Signature", "sig")
                        .content("{\"signed\":true}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    @Test
    void invalidSignatureIs400AndStorageFailureIs503WithoutEnvelope() throws Exception {
        verifierFailure = new WebhookVerificationException(
                WebhookVerificationException.Reason.INVALID_SIGNATURE);
        mvc.perform(post("/webhooks/v1/payments/stripe").content("bad"))
                .andExpect(status().isBadRequest()).andExpect(content().string(""));

        verifierFailure = null;
        verifiedEvent = event(true);
        acceptanceFailure = new ProviderReceiptUnavailableException(new IllegalStateException("database"));
        mvc.perform(post("/webhooks/v1/payments/stripe").content("valid"))
                .andExpect(status().isServiceUnavailable()).andExpect(content().string(""));
    }

    private VerifiedProviderEvent event(boolean supported) {
        return new VerifiedProviderEvent("evt_test", "checkout.session.completed", "2026-07-29.dahlia",
                false, supported ? "cs_test" : null, null, null, null, Instant.now(), Instant.now(), null,
                supported);
    }

    private final class StubVerifier extends StripeWebhookVerifier {
        StubVerifier() {
            super(new StripeCheckoutProperties(true, "test", URI.create("https://api.stripe.com"),
                    "sk_test", "pk_test", "whsec_test", "2026-07-29.dahlia", Duration.ofMinutes(5),
                    Duration.ofSeconds(5), Duration.ofSeconds(20), 2, URI.create("https://success.test"),
                    URI.create("https://cancel.test")), new StripeWebhookMapper());
        }

        @Override
        public VerifiedProviderEvent verify(byte[] rawBody, String signature) {
            if (verifierFailure != null) {
                throw verifierFailure;
            }
            return verifiedEvent;
        }
    }
}
