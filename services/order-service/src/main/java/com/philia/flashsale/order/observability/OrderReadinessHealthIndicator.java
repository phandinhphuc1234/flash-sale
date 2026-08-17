package com.philia.flashsale.order.observability;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Reports the dependencies required to serve committed Orders while keeping broker outages out of
 * the readiness decision. Consumer and outbox state is exposed as bounded diagnostic details.
 */
public final class OrderReadinessHealthIndicator implements HealthIndicator {
    private final BooleanSupplier postgresAvailable;
    private final BooleanSupplier consumerAvailable;
    private final LongSupplier outboxBacklog;
    private final Supplier<Duration> oldestPendingAge;
    private final OrderObservability observability;

    public OrderReadinessHealthIndicator(BooleanSupplier postgresAvailable,
            BooleanSupplier consumerAvailable) {
        this(postgresAvailable, consumerAvailable, () -> 0L, () -> Duration.ZERO,
                OrderObservability.noop());
    }

    public OrderReadinessHealthIndicator(BooleanSupplier postgresAvailable,
            BooleanSupplier consumerAvailable, LongSupplier outboxBacklog,
            Supplier<Duration> oldestPendingAge) {
        this(postgresAvailable, consumerAvailable, outboxBacklog, oldestPendingAge,
                OrderObservability.noop());
    }

    public OrderReadinessHealthIndicator(BooleanSupplier postgresAvailable,
            BooleanSupplier consumerAvailable, LongSupplier outboxBacklog,
            Supplier<Duration> oldestPendingAge, OrderObservability observability) {
        this.postgresAvailable = Objects.requireNonNull(postgresAvailable, "postgresAvailable");
        this.consumerAvailable = Objects.requireNonNull(consumerAvailable, "consumerAvailable");
        this.outboxBacklog = Objects.requireNonNull(outboxBacklog, "outboxBacklog");
        this.oldestPendingAge = Objects.requireNonNull(oldestPendingAge, "oldestPendingAge");
        this.observability = Objects.requireNonNull(observability, "observability");
    }

    @Override
    public Health health() {
        boolean postgres = available(postgresAvailable);
        boolean consumer = available(consumerAvailable);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("requiredDependencies", List.of("postgres"));
        details.put("postgres", postgres ? "up" : "down");
        details.put("consumer", consumer ? "up" : "down");

        // Do not issue additional JDBC queries after the primary dependency check fails. During a
        // database restart those queries would wait on the same exhausted pool and make readiness
        // itself block for the datasource connection timeout.
        if (!postgres) {
            Duration age = Duration.ZERO;
            observability.recordOutboxBacklog(0L, age);
            details.put("outboxBacklog", 0L);
            details.put("outboxOldestPendingAgeSeconds", 0.0d);
            details.put("unavailableDependencies", List.of("postgres"));
            return Health.down().withDetails(details).build();
        }

        long backlog = safeBacklog();
        Duration age = safeAge();
        observability.recordOutboxBacklog(backlog, age);
        details.put("outboxBacklog", backlog);
        details.put("outboxOldestPendingAgeSeconds", age.toMillis() / 1000d);
        return Health.up().withDetails(details).build();
    }

    private long safeBacklog() {
        try {
            return Math.max(0L, outboxBacklog.getAsLong());
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private Duration safeAge() {
        try {
            Duration value = oldestPendingAge.get();
            return value == null || value.isNegative() ? Duration.ZERO : value;
        } catch (RuntimeException ignored) {
            return Duration.ZERO;
        }
    }

    private boolean available(BooleanSupplier dependency) {
        try {
            return dependency.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
