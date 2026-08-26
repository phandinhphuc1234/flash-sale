package com.philia.flashsale.flashsale.observability;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.time.Duration;

/**
 * Readiness requires only dependencies that can safely admit and durably persist a reservation.
 * Kafka and Schema Registry remain outbox-backlog concerns after durable acceptance.
 */
public final class FlashSaleReadinessHealthIndicator implements HealthIndicator {
    private final BooleanSupplier postgresAvailable;
    private final BooleanSupplier redisAvailable;
    private final LongSupplier outboxBacklog;
    private final Supplier<Duration> oldestOutboxAge;
    private final LongSupplier reconciliationBacklog;
    private final Supplier<Duration> oldestReconciliationAge;
    private final FlashSaleObservability observability;

    public FlashSaleReadinessHealthIndicator(BooleanSupplier postgresAvailable,
            BooleanSupplier redisAvailable) {
        this(postgresAvailable, redisAvailable, () -> 0L, () -> Duration.ZERO,
                () -> 0L, () -> Duration.ZERO, FlashSaleObservability.noop());
    }

    public FlashSaleReadinessHealthIndicator(BooleanSupplier postgresAvailable,
            BooleanSupplier redisAvailable, LongSupplier outboxBacklog,
            Supplier<Duration> oldestOutboxAge, LongSupplier reconciliationBacklog,
            Supplier<Duration> oldestReconciliationAge, FlashSaleObservability observability) {
        this.postgresAvailable = Objects.requireNonNull(postgresAvailable, "postgresAvailable");
        this.redisAvailable = Objects.requireNonNull(redisAvailable, "redisAvailable");
        this.outboxBacklog = Objects.requireNonNull(outboxBacklog, "outboxBacklog");
        this.oldestOutboxAge = Objects.requireNonNull(oldestOutboxAge, "oldestOutboxAge");
        this.reconciliationBacklog = Objects.requireNonNull(reconciliationBacklog, "reconciliationBacklog");
        this.oldestReconciliationAge = Objects.requireNonNull(oldestReconciliationAge,
                "oldestReconciliationAge");
        this.observability = Objects.requireNonNull(observability, "observability");
    }

    @Override
    public Health health() {
        List<String> unavailable = new ArrayList<>(2);
        if (!isAvailable(postgresAvailable)) {
            unavailable.add("postgres");
        }
        if (!isAvailable(redisAvailable)) {
            unavailable.add("redis");
        }
        if (!unavailable.isEmpty()) {
            return Health.down().withDetail("unavailableDependencies", List.copyOf(unavailable)).build();
        }
        long pendingOutbox = safeLong(outboxBacklog);
        Duration outboxAge = safeDuration(oldestOutboxAge);
        long pendingReconciliation = safeLong(reconciliationBacklog);
        Duration reconciliationAge = safeDuration(oldestReconciliationAge);
        observability.recordOperationalBacklog(pendingOutbox, outboxAge,
                pendingReconciliation, reconciliationAge);
        return Health.up()
                .withDetail("requiredDependencies", List.of("postgres", "redis"))
                .withDetail("outboxBacklog", pendingOutbox)
                .withDetail("outboxOldestPendingAgeSeconds", outboxAge.toMillis() / 1000d)
                .withDetail("redisReconciliationBacklog", pendingReconciliation)
                .withDetail("redisReconciliationOldestAgeSeconds", reconciliationAge.toMillis() / 1000d)
                .build();
    }

    private long safeLong(LongSupplier supplier) {
        try {
            return Math.max(0L, supplier.getAsLong());
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private Duration safeDuration(Supplier<Duration> supplier) {
        try {
            Duration value = supplier.get();
            return value == null || value.isNegative() ? Duration.ZERO : value;
        } catch (RuntimeException ignored) {
            return Duration.ZERO;
        }
    }

    private boolean isAvailable(BooleanSupplier dependency) {
        try {
            return dependency.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }
}
