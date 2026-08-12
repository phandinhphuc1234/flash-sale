package com.philia.flashsale.gateway.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * Terminal result of attempting to evaluate quota for one request.
 */
public record RateLimitDecision(
        Outcome outcome,
        long remainingCredit,
        Duration retryAfter,
        RateLimitFailureType failureType) {

    private static final long NO_REMAINING_CREDIT = -1L;

    public RateLimitDecision {
        outcome = Objects.requireNonNull(outcome, "outcome must not be null");

        if (outcome == Outcome.ALLOWED && remainingCredit < 0) {
            throw new IllegalArgumentException("allowed decision must include non-negative remaining credit");
        }
        if (outcome == Outcome.REJECTED) {
            if (remainingCredit < 0) {
                throw new IllegalArgumentException("rejected decision must include non-negative remaining credit");
            }
            if (retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()) {
                throw new IllegalArgumentException("rejected decision must include a positive retry delay");
            }
        }
        if (outcome != Outcome.REJECTED && retryAfter != null) {
            throw new IllegalArgumentException("only rejected decisions may carry retry delay");
        }
        if (outcome == Outcome.FAIL_OPEN && failureType == null) {
            throw new IllegalArgumentException("fail-open decision must include a failure type");
        }
        if (outcome != Outcome.FAIL_OPEN && failureType != null) {
            throw new IllegalArgumentException("only fail-open decisions may carry a failure type");
        }
    }

    public static RateLimitDecision allowed(long remainingCredit) {
        return new RateLimitDecision(Outcome.ALLOWED, remainingCredit, null, null);
    }

    public static RateLimitDecision rejected(long remainingCredit, Duration retryAfter) {
        return new RateLimitDecision(Outcome.REJECTED, remainingCredit, retryAfter, null);
    }

    public static RateLimitDecision identityUnavailable() {
        return new RateLimitDecision(Outcome.IDENTITY_UNAVAILABLE, NO_REMAINING_CREDIT, null, null);
    }

    public static RateLimitDecision failOpen(RateLimitFailureType failureType) {
        return new RateLimitDecision(Outcome.FAIL_OPEN, NO_REMAINING_CREDIT, null, failureType);
    }

    public boolean allowed() {
        return outcome == Outcome.ALLOWED;
    }

    public boolean rejected() {
        return outcome == Outcome.REJECTED;
    }

    public boolean forwardedWithoutQuotaDecision() {
        return outcome == Outcome.IDENTITY_UNAVAILABLE || outcome == Outcome.FAIL_OPEN;
    }

    public enum Outcome {
        ALLOWED,
        REJECTED,
        IDENTITY_UNAVAILABLE,
        FAIL_OPEN
    }
}
