package com.philia.flashsale.flashsale.observability;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Readiness requires only dependencies that can safely admit and durably persist a reservation.
 * Kafka and Schema Registry remain outbox-backlog concerns after durable acceptance.
 */
public final class FlashSaleReadinessHealthIndicator implements HealthIndicator {
    private final BooleanSupplier postgresAvailable;
    private final BooleanSupplier redisAvailable;

    public FlashSaleReadinessHealthIndicator(BooleanSupplier postgresAvailable,
            BooleanSupplier redisAvailable) {
        this.postgresAvailable = Objects.requireNonNull(postgresAvailable, "postgresAvailable");
        this.redisAvailable = Objects.requireNonNull(redisAvailable, "redisAvailable");
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
        return Health.up().withDetail("requiredDependencies", List.of("postgres", "redis")).build();
    }

    private boolean isAvailable(BooleanSupplier dependency) {
        try {
            return dependency.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }
}
