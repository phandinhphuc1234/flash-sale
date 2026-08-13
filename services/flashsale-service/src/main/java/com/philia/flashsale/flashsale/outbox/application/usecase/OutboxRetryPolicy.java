package com.philia.flashsale.flashsale.outbox.application.usecase;

import java.time.Duration;
import java.util.Objects;

/** Exponential retry delay with the approved 60-second maximum. */
public final class OutboxRetryPolicy {
    private final Duration cap;

    public OutboxRetryPolicy(Duration cap) {
        this.cap = Objects.requireNonNull(cap, "cap");
        if (cap.isNegative() || cap.isZero()) {
            throw new IllegalArgumentException("retry cap must be positive");
        }
    }

    public Duration delayForAttempt(int attemptCount) {
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be positive");
        }
        long capSeconds = Math.max(1, cap.toSeconds());
        int shift = Math.min(attemptCount - 1, 62);
        long seconds = 1L << shift;
        return Duration.ofSeconds(Math.min(seconds > 0 ? seconds : Long.MAX_VALUE, capSeconds));
    }

    public Duration cap() {
        return cap;
    }
}
