package com.philia.flashsale.gateway.ratelimit;

import java.util.Objects;

/**
 * Controlled exception raised only by quota coordination boundaries.
 */
public final class RateLimitCoordinatorException extends RuntimeException {

    private final RateLimitFailureType failureType;

    public RateLimitCoordinatorException(RateLimitFailureType failureType, String message) {
        super(message);
        this.failureType = Objects.requireNonNull(failureType, "failure type must not be null");
    }

    public RateLimitCoordinatorException(RateLimitFailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = Objects.requireNonNull(failureType, "failure type must not be null");
    }

    public RateLimitFailureType failureType() {
        return failureType;
    }
}
