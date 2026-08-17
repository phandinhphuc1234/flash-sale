package com.philia.flashsale.payment.payment.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentAttemptTests {

    private static final Instant CREATED = Instant.parse("2026-08-17T00:00:00Z");

    @Test
    void attemptKeepsStableProviderIdentityAcrossStateChanges() {
        PaymentAttempt attempt = PaymentAttempt.create(UUID.randomUUID(), UUID.randomUUID(), 1,
                PaymentProvider.STRIPE, "payment-attempt-key", CREATED, CREATED.plus(23, ChronoUnit.HOURS));
        attempt.markOpen("cs_test_123", CREATED.plus(30, ChronoUnit.MINUTES), CREATED.plusSeconds(1));
        attempt.markProcessing("unpaid", CREATED.plusSeconds(2));
        attempt.markUnknown("unknown", CREATED.plusSeconds(3));

        assertEquals("payment-attempt-key", attempt.providerIdempotencyKey());
        assertEquals("cs_test_123", attempt.providerSessionId());
        assertEquals(PaymentAttemptStatus.UNKNOWN, attempt.status());
    }

    @Test
    void latePaidOutcomeCanMoveFailedOrExpiredAttemptToSuccess() {
        PaymentAttempt attempt = PaymentAttempt.create(UUID.randomUUID(), UUID.randomUUID(), 1,
                PaymentProvider.STRIPE, "payment-attempt-key", CREATED, CREATED.plus(23, ChronoUnit.HOURS));
        attempt.markFailed(FailureReason.PROVIDER_TERMINAL_FAILURE, CREATED.plusSeconds(1));
        attempt.markSucceeded("cs_test_123", "pi_test_123", CREATED.plusSeconds(2));

        assertEquals(PaymentAttemptStatus.SUCCEEDED, attempt.status());
        assertEquals(null, attempt.failureReason());
    }

    @Test
    void invalidAttemptNumberAndIdentityAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PaymentAttempt.create(UUID.randomUUID(), UUID.randomUUID(), 4,
                        PaymentProvider.STRIPE, "key", CREATED, CREATED.plus(23, ChronoUnit.HOURS)));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentAttempt.create(UUID.randomUUID(), UUID.randomUUID(), 1,
                        PaymentProvider.STRIPE, " ", CREATED, CREATED.plus(23, ChronoUnit.HOURS)));
    }
}
