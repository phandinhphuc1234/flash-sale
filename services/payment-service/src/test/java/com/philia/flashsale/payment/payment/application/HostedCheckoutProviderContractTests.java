package com.philia.flashsale.payment.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutCreateRequest;
import com.philia.flashsale.payment.payment.application.model.provider.ProviderCheckoutState;
import com.philia.flashsale.payment.payment.domain.exception.InvalidMoneyException;
import com.philia.flashsale.payment.payment.domain.model.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Provider-neutral contract tests keep money and server-owned metadata exact before Stripe mapping. */
class HostedCheckoutProviderContractTests {
    @Test
    void vndUsesExactZeroDecimalProviderAmount() {
        assertThat(Money.of(new BigDecimal("250000.0000"), "VND").toProviderMinorUnits())
                .isEqualTo(250000L);
    }

    @Test
    void fractionalVndIsRejectedInsteadOfRounded() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("1.25"), "VND").toProviderMinorUnits())
                .isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    void createRequestCarriesOnlyAllowlistedServerOwnedMetadata() {
        var request = new HostedCheckoutCreateRequest("payment-1", "order-1", "attempt-1",
                new BigDecimal("10"), "VND", "provider-key", Instant.parse("2026-08-17T00:05:00Z"),
                "https://example.test/success", "https://example.test/cancel",
                Map.of("paymentId", "payment-1", "orderId", "order-1", "attemptId", "attempt-1"));

        assertThat(request.metadata()).containsOnlyKeys("paymentId", "orderId", "attemptId");
        assertThat(request.currency()).isEqualTo("VND");
    }
}
