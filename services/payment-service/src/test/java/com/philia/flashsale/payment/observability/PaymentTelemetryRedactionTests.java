package com.philia.flashsale.payment.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentTelemetryRedactionTests {

    @Test
    void redactsSecretsUrlsRawBodiesAndProviderHeaders() {
        assertThat(PaymentObservability.redact("authorization", "Bearer secret-token"))
                .isEqualTo("[REDACTED]");
        assertThat(PaymentObservability.redact("checkout_url", "https://checkout.stripe.com/cs_test"))
                .isEqualTo("[REDACTED]");
        assertThat(PaymentObservability.redact("raw_body", "{\"payment_method\":\"card\"}"))
                .isEqualTo("[REDACTED]");
        assertThat(PaymentObservability.safeIdentifier("https://api.stripe.com/v1"))
                .isEqualTo("redacted");
        assertThat(PaymentObservability.redactException(new RuntimeException("sk_test_secret")))
                .isEqualTo("other");
    }

    @Test
    void allowsOnlySafeOpaqueIdentifiersAndBoundsLongValues() {
        assertThat(PaymentObservability.safeIdentifier("2f8a7a22-1d8d-4a63-8f8f-6ac4e22da2c0"))
                .contains("-");
        assertThat(PaymentObservability.safeIdentifier("raw provider/session"))
                .isEqualTo("redacted");
        assertThat(PaymentObservability.redact("category", "a".repeat(200)))
                .hasSize(129).endsWith("…");
    }
}
