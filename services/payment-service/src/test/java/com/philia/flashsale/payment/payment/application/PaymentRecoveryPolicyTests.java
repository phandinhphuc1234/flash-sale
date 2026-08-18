package com.philia.flashsale.payment.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.payment.payment.application.service.PaymentRecoveryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Deterministic proof for retry timing, replay safety, and manual-review escalation. */
class PaymentRecoveryPolicyTests {

    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    @Test
    void usesApprovedBackoffAndCapsAtTheLastDelay() {
        PaymentRecoveryPolicy policy = new PaymentRecoveryPolicy(
                List.of(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(10),
                        Duration.ofSeconds(30), Duration.ofSeconds(60)), 10, Duration.ofHours(23));

        assertThat(policy.backoffForAttempt(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.backoffForAttempt(2)).isEqualTo(Duration.ofSeconds(3));
        assertThat(policy.backoffForAttempt(3)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.backoffForAttempt(4)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.backoffForAttempt(5)).isEqualTo(Duration.ofSeconds(60));
        assertThat(policy.backoffForAttempt(60)).isEqualTo(Duration.ofSeconds(60));
        assertThat(policy.nextAttemptAt(NOW, 3)).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void distinguishesSafeReplayWindowBoundary() {
        PaymentRecoveryPolicy policy = new PaymentRecoveryPolicy();
        Instant safeUntil = NOW.plus(policy.safeReplayWindow());

        assertThat(policy.withinSafeReplayWindow(NOW, safeUntil)).isTrue();
        assertThat(policy.withinSafeReplayWindow(safeUntil, safeUntil)).isFalse();
        assertThat(policy.withinSafeReplayWindow(safeUntil.plusSeconds(1), safeUntil)).isFalse();
    }

    @Test
    void escalatesOnlyAfterConfiguredBoundedAttempts() {
        PaymentRecoveryPolicy policy = new PaymentRecoveryPolicy(
                List.of(Duration.ofSeconds(1)), 3, Duration.ofHours(23));

        assertThat(policy.shouldEscalateToManualReview(1)).isFalse();
        assertThat(policy.shouldEscalateToManualReview(2, "UNKNOWN")).isFalse();
        assertThat(policy.shouldEscalateToManualReview(3, "UNRECOGNIZED")).isTrue();
    }

    @Test
    void rejectsInvalidPolicyConfiguration() {
        assertThatThrownBy(() -> new PaymentRecoveryPolicy(List.of(Duration.ZERO), 3,
                Duration.ofHours(23))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentRecoveryPolicy(List.of(Duration.ofSeconds(1)), 0,
                Duration.ofHours(23))).isInstanceOf(IllegalArgumentException.class);
    }
}
