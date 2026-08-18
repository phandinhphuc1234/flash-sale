package com.philia.flashsale.payment.outbox.application.service;

import java.time.Duration;
import java.time.Instant;

/** Capped exponential retry policy for technical Kafka/Schema Registry failures. */
public final class PaymentOutboxRetryPolicy {

    private PaymentOutboxRetryPolicy() {
    }

    /** Returns the next due time without ever creating a terminal business failure. */
    public static Instant nextAttemptAt(Instant now, int failedAttempt, Duration cap) {
        if (now == null || failedAttempt <= 0 || cap == null || cap.isNegative() || cap.isZero()) {
            return now;
        }
        long seconds = 1L << Math.min(failedAttempt, 30);
        return now.plus(Duration.ofSeconds(Math.min(seconds, cap.getSeconds())));
    }
}
