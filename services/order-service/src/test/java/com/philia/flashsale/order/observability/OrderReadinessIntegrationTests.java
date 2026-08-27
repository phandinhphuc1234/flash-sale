package com.philia.flashsale.order.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

/** Verifies the readiness contract independently from a live broker or database. */
class OrderReadinessIntegrationTests {

    @Test
    void reportsSafeOperationalSignalsWithoutLeakingDependencyErrors() {
        OrderReadinessHealthIndicator indicator = new OrderReadinessHealthIndicator(
                () -> { throw new IllegalStateException("password=do-not-leak"); },
                () -> { throw new IllegalStateException("broker=do-not-leak"); },
                () -> { throw new IllegalStateException("sql=do-not-leak"); },
                () -> { throw new IllegalStateException("trace=do-not-leak"); });

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().toString())
                .doesNotContain("password", "do-not-leak", "broker", "sql", "trace");
        assertThat(health.getDetails()).containsEntry("outboxBacklog", 0L)
                .containsEntry("outboxOldestPendingAgeSeconds", 0.0d);
    }

    @Test
    void preservesNonNegativeBacklogAndAgeSignals() {
        OrderReadinessHealthIndicator indicator = new OrderReadinessHealthIndicator(
                () -> true, () -> true, () -> -1L, () -> Duration.ofSeconds(-1));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).containsEntry("outboxBacklog", 0L)
                .containsEntry("outboxOldestPendingAgeSeconds", 0.0d);
    }

    @Test
    void doesNotQueryOutboxWhenPostgresIsUnavailable() {
        AtomicBoolean backlogQueried = new AtomicBoolean();
        AtomicBoolean ageQueried = new AtomicBoolean();
        OrderReadinessHealthIndicator indicator = new OrderReadinessHealthIndicator(
                () -> false,
                () -> true,
                () -> {
                    backlogQueried.set(true);
                    return 9L;
                },
                () -> {
                    ageQueried.set(true);
                    return Duration.ofMinutes(2);
                });

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(backlogQueried).isFalse();
        assertThat(ageQueried).isFalse();
        assertThat(health.getDetails()).containsEntry("outboxBacklog", 0L)
                .containsEntry("outboxOldestPendingAgeSeconds", 0.0d);
    }

    @Test
    void exposesManualReviewAndOldestSagaStepAsNonGatingDiagnostics() {
        OrderReadinessHealthIndicator indicator = new OrderReadinessHealthIndicator(
                () -> true, () -> false, () -> 2L, () -> Duration.ofSeconds(10),
                () -> Map.of("PAYMENT_PENDING", 4L, "MANUAL_REVIEW", 2L),
                () -> Duration.ofSeconds(75), OrderObservability.noop());

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("consumer", "down")
                .containsEntry("sagaManualReviewCount", 2L)
                .containsEntry("sagaOldestStepAgeSeconds", 75.0d);
    }
}
