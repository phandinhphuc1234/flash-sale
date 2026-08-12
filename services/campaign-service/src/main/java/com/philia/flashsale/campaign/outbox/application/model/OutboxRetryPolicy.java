package com.philia.flashsale.campaign.outbox.application.model;

import java.time.Duration;
import java.time.Instant;

/** Approved bounded exponential backoff: min(cap, 2^failedAttempt seconds). */
public final class OutboxRetryPolicy {

    private OutboxRetryPolicy() {
    }

    public static Instant nextAttemptAt(Instant now, int failedAttempt, Duration cap) {
        if (failedAttempt <= 0 || cap == null || cap.isNegative() || cap.isZero()) {
            return now;
        }
        long seconds = 1L << Math.min(failedAttempt, 30);
        return now.plus(Duration.ofSeconds(Math.min(seconds, cap.getSeconds())));
    }
}
