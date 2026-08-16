package com.philia.flashsale.order.outbox.application.usecase;

import java.time.Duration;
import java.util.Objects;

/** Exponential outbox retry delay with the approved 60-second maximum cap. */
public final class OrderOutboxRetryPolicy {
    private static final Duration INITIAL_DELAY = Duration.ofSeconds(1);

    private final Duration cap;

    public OrderOutboxRetryPolicy(Duration cap) {
        this.cap = Objects.requireNonNull(cap, "cap");
        if (cap.isZero() || cap.isNegative()) {
            throw new IllegalArgumentException("retry cap must be positive");
        }
    }

    public Duration delayForAttempt(int attemptCount) {
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be positive");
        }
        int shift = Math.min(attemptCount - 1, 62);
        try {
            Duration delay = INITIAL_DELAY.multipliedBy(1L << shift);
            return delay.compareTo(cap) > 0 ? cap : delay;
        } catch (ArithmeticException overflow) {
            return cap;
        }
    }

    public Duration cap() {
        return cap;
    }
}
