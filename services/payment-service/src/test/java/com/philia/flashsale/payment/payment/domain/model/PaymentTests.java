package com.philia.flashsale.payment.payment.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTests {

    private static final Instant CREATED = Instant.parse("2026-08-17T00:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-08-17T00:05:00Z");

    @Test
    void acceptedSnapshotIsImmutableAndStartsPending() {
        Payment payment = payment();

        assertEquals(PaymentStatus.PENDING, payment.status());
        assertEquals("VND", payment.currency());
        assertEquals(100_000L, payment.amount().longValueExact());
        assertEquals(0L, payment.aggregateVersion());
    }

    @Test
    void onlyOneUnresolvedAttemptCanBeAllocated() {
        Payment payment = payment();
        payment.allocateAttempt(UUID.randomUUID(), "key-1", CREATED.plusSeconds(1),
                CREATED.plus(23, ChronoUnit.HOURS));

        assertThrows(IllegalArgumentException.class,
                () -> payment.allocateAttempt(UUID.randomUUID(), "key-2", CREATED.plusSeconds(2),
                        CREATED.plus(23, ChronoUnit.HOURS)));
    }

    @Test
    void atMostThreeSequentialAttemptsAreAllowed() {
        Payment payment = payment();
        for (int index = 1; index <= 3; index++) {
            PaymentAttempt attempt = payment.allocateAttempt(UUID.randomUUID(), "key-" + index,
                    CREATED.plusSeconds(index), CREATED.plus(23, ChronoUnit.HOURS));
            payment.markProviderTerminalFailure(attempt.id(), CREATED.plusSeconds(index));
        }

        assertEquals(3, payment.attemptsUsed());
        assertEquals(PaymentStatus.FAILED, payment.status());
        assertEquals(FailureReason.CHECKOUT_ATTEMPT_LIMIT_REACHED, payment.failureReason());
        assertThrows(IllegalArgumentException.class,
                () -> payment.allocateAttempt(UUID.randomUUID(), "key-4", CREATED.plusSeconds(10),
                        CREATED.plus(23, ChronoUnit.HOURS)));
    }

    @Test
    void verifiedSuccessDominatesEarlierFailureAndIncrementsVersion() {
        Payment payment = payment();
        PaymentAttempt attempt = payment.allocateAttempt(UUID.randomUUID(), "key-1",
                CREATED.plusSeconds(1), CREATED.plus(23, ChronoUnit.HOURS));
        payment.markProviderTerminalFailure(attempt.id(), CREATED.plusSeconds(2));
        assertEquals(PaymentStatus.PENDING, payment.status());

        payment.markProviderPaid(attempt.id(), "cs_test_1", "pi_test_1", CREATED.plusSeconds(3));
        assertEquals(PaymentStatus.SUCCEEDED, payment.status());
        assertEquals(1L, payment.aggregateVersion());
        assertEquals(PaymentAttemptStatus.SUCCEEDED, attempt.status());

        payment.markAttemptUnknown(attempt.id(), "late-unknown", CREATED.plusSeconds(4));
        payment.markProviderTerminalFailure(attempt.id(), CREATED.plusSeconds(5));
        assertEquals(PaymentStatus.SUCCEEDED, payment.status());
    }

    @Test
    void deadlineExpiryIsARealTerminalBusinessOutcome() {
        Payment payment = payment();
        payment.expire(DEADLINE);

        assertEquals(PaymentStatus.EXPIRED, payment.status());
        assertEquals(FailureReason.PAYMENT_DEADLINE_EXPIRED, payment.failureReason());
        assertEquals(1L, payment.aggregateVersion());
    }

    private Payment payment() {
        return Payment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Money.vnd(100_000), DEADLINE, CREATED);
    }
}
