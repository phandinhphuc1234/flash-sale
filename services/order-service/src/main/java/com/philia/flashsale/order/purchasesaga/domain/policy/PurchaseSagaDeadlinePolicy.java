package com.philia.flashsale.order.purchasesaga.domain.policy;

import com.philia.flashsale.order.purchasesaga.domain.exception.InvalidPurchaseSagaException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Applies the approved thirty-second payment safety margin before reservation expiry. */
public final class PurchaseSagaDeadlinePolicy {
    public static final Duration PAYMENT_SAFETY_MARGIN = Duration.ofSeconds(30);

    private PurchaseSagaDeadlinePolicy() {
    }

    public static Instant paymentDeadline(Instant reservationExpiresAt, Instant createdAt) {
        Objects.requireNonNull(reservationExpiresAt, "reservationExpiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Instant deadline = reservationExpiresAt.minus(PAYMENT_SAFETY_MARGIN);
        if (!deadline.isAfter(createdAt)) {
            throw new InvalidPurchaseSagaException(
                    "reservation expiry does not leave a future payment window");
        }
        return deadline;
    }
}
