package com.philia.flashsale.payment.payment.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic reconciliation policy. It owns only retry timing and safe replay decisions; it
 * never turns an unavailable provider into a financial outcome.
 */
public final class PaymentRecoveryPolicy {

    public static final List<Duration> DEFAULT_BACKOFF = List.of(
            Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(10),
            Duration.ofSeconds(30), Duration.ofSeconds(60));
    public static final Duration DEFAULT_SAFE_REPLAY_WINDOW = Duration.ofHours(23);
    public static final int DEFAULT_MAX_ATTEMPTS = 10;

    private final List<Duration> retryBackoff;
    private final int maxAttempts;
    private final Duration safeReplayWindow;

    public PaymentRecoveryPolicy() {
        this(DEFAULT_BACKOFF, DEFAULT_MAX_ATTEMPTS, DEFAULT_SAFE_REPLAY_WINDOW);
    }

    public PaymentRecoveryPolicy(List<Duration> retryBackoff, int maxAttempts,
            Duration safeReplayWindow) {
        if (retryBackoff == null || retryBackoff.isEmpty()
                || retryBackoff.stream().anyMatch(this::isNotPositive)) {
            throw new IllegalArgumentException("recovery backoff must contain positive durations");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("recovery max attempts must be positive");
        }
        if (safeReplayWindow == null || safeReplayWindow.isZero() || safeReplayWindow.isNegative()) {
            throw new IllegalArgumentException("safe replay window must be positive");
        }
        this.retryBackoff = List.copyOf(new ArrayList<>(retryBackoff));
        this.maxAttempts = maxAttempts;
        this.safeReplayWindow = safeReplayWindow;
    }

    public PaymentRecoveryPolicy(Duration safeReplayWindow, int maxAttempts,
            List<Duration> retryBackoff) {
        this(retryBackoff, maxAttempts, safeReplayWindow);
    }

    /** Returns the configured delay for a one-based worker attempt, capped at the last value. */
    public Duration backoffForAttempt(int attemptCount) {
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be positive");
        }
        return retryBackoff.get(Math.min(attemptCount, retryBackoff.size()) - 1);
    }

    public Instant nextAttemptAt(Instant now, int attemptCount) {
        return Objects.requireNonNull(now, "now").plus(backoffForAttempt(attemptCount));
    }

    /** True only while retrying the original provider create operation is still safe. */
    public boolean withinSafeReplayWindow(Instant now, Instant safeReplayUntil) {
        return now != null && safeReplayUntil != null && now.isBefore(safeReplayUntil);
    }

    public boolean shouldEscalateToManualReview(int attemptCount) {
        return attemptCount >= maxAttempts;
    }

    public boolean shouldEscalateToManualReview(int attemptCount, String providerState) {
        // Unknown/unrecognized state is never converted to a terminal business result.
        return shouldEscalateToManualReview(attemptCount);
    }

    public List<Duration> retryBackoff() {
        return retryBackoff;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public Duration safeReplayWindow() {
        return safeReplayWindow;
    }

    private boolean isNotPositive(Duration value) {
        return value == null || value.isZero() || value.isNegative();
    }
}
