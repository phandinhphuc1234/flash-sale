package com.philia.flashsale.payment.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.payment.payment.domain.model.Payment;
import com.philia.flashsale.payment.payment.domain.model.PaymentAttempt;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Outcome-policy tests for duplicate, out-of-order, uncertain, and late provider truth. */
class ApplyProviderOutcomeServiceTests {
    private static final Instant CREATED = Instant.parse("2026-08-18T08:00:00Z");

    @Test
    void paidOutcomeIsIdempotentAndSuccessDominant() {
        Payment payment = payment(CREATED.plusSeconds(300));
        PaymentAttempt attempt = openAttempt(payment, CREATED);

        payment.markProviderPaid(attempt.id(), "cs_test", "pi_test", CREATED.plusSeconds(1));
        long version = payment.aggregateVersion();
        payment.markProviderPaid(attempt.id(), "cs_test", "pi_test", CREATED.plusSeconds(2));

        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.aggregateVersion()).isEqualTo(version);
    }

    @Test
    void expiredOrFailedAttemptCanRetryBeforeDeadlineButLatePaidWins() {
        Payment payment = payment(CREATED.plusSeconds(300));
        PaymentAttempt attempt = openAttempt(payment, CREATED);

        payment.markProviderExpired(attempt.id(), CREATED.plusSeconds(1));
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(attempt.status()).isEqualTo(com.philia.flashsale.payment.payment.domain.model.PaymentAttemptStatus.EXPIRED);

        PaymentAttempt second = payment.allocateAttempt(UUID.randomUUID(), "provider-key-2",
                CREATED.plusSeconds(2), CREATED.plusSeconds(3600));
        payment.markAttemptOpen(second.id(), "cs_second", CREATED.plusSeconds(3600), CREATED.plusSeconds(3));
        payment.markProviderTerminalFailure(second.id(), CREATED.plusSeconds(4));
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);

        payment.markProviderPaid(second.id(), "cs_second", "pi_second", CREATED.plusSeconds(5));
        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void processingAndUnknownDoNotCreateFailureFact() {
        Payment payment = payment(CREATED.plusSeconds(300));
        PaymentAttempt attempt = openAttempt(payment, CREATED);

        payment.markAttemptProcessing(attempt.id(), "processing", CREATED.plusSeconds(1));
        assertThat(payment.status()).isEqualTo(PaymentStatus.PROCESSING);
        payment.markAttemptUnknown(attempt.id(), "provider-timeout", CREATED.plusSeconds(2));
        assertThat(payment.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(payment.aggregateVersion()).isZero();
    }

    private Payment payment(Instant deadline) {
        return Payment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100000"), "VND", deadline, CREATED);
    }

    private PaymentAttempt openAttempt(Payment payment, Instant now) {
        PaymentAttempt attempt = payment.allocateAttempt(UUID.randomUUID(), "provider-key-1", now,
                now.plusSeconds(3600));
        payment.markAttemptOpen(attempt.id(), "cs_test", now.plusSeconds(1800), now.plusSeconds(1));
        return payment.activeAttempt();
    }
}
