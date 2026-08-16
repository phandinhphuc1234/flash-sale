package com.philia.flashsale.order.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
}
