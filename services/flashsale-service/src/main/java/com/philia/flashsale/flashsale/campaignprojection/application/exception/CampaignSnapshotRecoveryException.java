package com.philia.flashsale.flashsale.campaignprojection.application.exception;

import java.time.Duration;
import java.util.Objects;

/** Application-owned failure used to schedule a later Campaign snapshot recovery attempt. */
public final class CampaignSnapshotRecoveryException extends RuntimeException {

    public enum Failure {
        SECURITY,
        NOT_FOUND,
        NOT_RECOVERABLE,
        RATE_LIMITED,
        UNAVAILABLE,
        INVALID_RESPONSE
    }

    private final Failure failure;
    private final Duration retryAfter;

    public CampaignSnapshotRecoveryException(Failure failure, Duration retryAfter) {
        super("Campaign snapshot recovery is unavailable: " + Objects.requireNonNull(failure));
        this.failure = failure;
        this.retryAfter = requirePositiveDelay(retryAfter);
    }

    public CampaignSnapshotRecoveryException(Failure failure, Duration retryAfter, Throwable cause) {
        super("Campaign snapshot recovery is unavailable: " + Objects.requireNonNull(failure), cause);
        this.failure = failure;
        this.retryAfter = requirePositiveDelay(retryAfter);
    }

    public Failure failure() {
        return failure;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    private static Duration requirePositiveDelay(Duration value) {
        Objects.requireNonNull(value, "retryAfter");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("retryAfter must be positive");
        }
        return value;
    }
}
