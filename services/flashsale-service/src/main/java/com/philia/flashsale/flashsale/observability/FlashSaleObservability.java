package com.philia.flashsale.flashsale.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Creates bounded observations and duration metrics at Flash Sale runtime boundaries.
 *
 * <p>Business identifiers must never be supplied here: the operation enum fixes every metric tag
 * value before an adapter invokes it.</p>
 */
@Component
public final class FlashSaleObservability {
    private static final String OUTCOME_TAG = "outcome";

    public static final String OPERATION_DURATION = "flashsale.operation.duration";
    public static final String CONSUMER_OUTCOME_TOTAL = "flashsale.reservation.command.outcome.total";
    public static final String DLT_PUBLICATION_TOTAL = "flashsale.reservation.command.dlt.publication.total";
    public static final String OUTBOX_BACKLOG = "flashsale.outbox.backlog";
    public static final String OUTBOX_OLDEST_PENDING_AGE = "flashsale.outbox.oldest_pending_age_seconds";
    public static final String REDIS_RECONCILIATION_BACKLOG = "flashsale.redis.reconciliation.backlog";
    public static final String REDIS_RECONCILIATION_OLDEST_AGE = "flashsale.redis.reconciliation.oldest_age_seconds";

    private static final FlashSaleObservability NOOP = new FlashSaleObservability();

    private final ObservationRegistry observations;
    private final MeterRegistry metrics;
    private volatile double outboxBacklog;
    private volatile double outboxOldestPendingAge;
    private volatile double reconciliationBacklog;
    private volatile double reconciliationOldestAge;

    public FlashSaleObservability(ObservationRegistry observations, MeterRegistry metrics) {
        this.observations = Objects.requireNonNull(observations, "observations");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        Gauge.builder(OUTBOX_BACKLOG, this, value -> value.outboxBacklog)
                .description("Number of unpublished Flash Sale outbox rows")
                .register(metrics);
        Gauge.builder(OUTBOX_OLDEST_PENDING_AGE, this, value -> value.outboxOldestPendingAge)
                .description("Age in seconds of the oldest unpublished Flash Sale outbox row")
                .register(metrics);
        Gauge.builder(REDIS_RECONCILIATION_BACKLOG, this, value -> value.reconciliationBacklog)
                .description("Final reservation rows waiting for Redis projection reconciliation")
                .register(metrics);
        Gauge.builder(REDIS_RECONCILIATION_OLDEST_AGE, this, value -> value.reconciliationOldestAge)
                .description("Age in seconds of the oldest unreconciled final reservation")
                .register(metrics);
    }

    private FlashSaleObservability() {
        this.observations = null;
        this.metrics = null;
    }

    /** Returns a zero-cost adapter fallback for direct unit and integration construction. */
    public static FlashSaleObservability noop() {
        return NOOP;
    }

    public <T> T observe(Operation operation, Supplier<T> action) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(action, "action");
        if (metrics == null) {
            return action.get();
        }

        Timer.Sample sample = Timer.start(metrics);
        Observation observation = Observation.createNotStarted(operation.observationName(), observations)
                .lowCardinalityKeyValue("operation", operation.tagValue())
                .lowCardinalityKeyValue("dependency", operation.dependency())
                .start();
        Outcome outcome = Outcome.SUCCESS;
        try (Observation.Scope ignored = observation.openScope()) {
            return action.get();
        } catch (RuntimeException exception) {
            outcome = Outcome.FAILURE;
            observation.error(exception);
            throw exception;
        } finally {
            observation.lowCardinalityKeyValue(OUTCOME_TAG, outcome.tagValue());
            observation.stop();
            sample.stop(timer(operation, outcome));
        }
    }

    public void observe(Operation operation, Runnable action) {
        observe(operation, () -> {
            action.run();
            return null;
        });
    }

    public void recordOperationalBacklog(long pendingOutbox, Duration oldestOutboxAge,
            long pendingReconciliation, Duration oldestReconciliationAge) {
        if (metrics == null) {
            return;
        }
        outboxBacklog = Math.max(0L, pendingOutbox);
        outboxOldestPendingAge = safeSeconds(oldestOutboxAge);
        reconciliationBacklog = Math.max(0L, pendingReconciliation);
        reconciliationOldestAge = safeSeconds(oldestReconciliationAge);
    }

    public void recordReservationCommandOutcome(String outcome) {
        if (metrics == null) {
            return;
        }
        Counter.builder(CONSUMER_OUTCOME_TOTAL)
                .description("Flash Sale reservation command consumer results")
                .tag(OUTCOME_TAG, boundedOutcome(outcome))
                .register(metrics)
                .increment();
    }

    public void recordReservationCommandDltPublication() {
        if (metrics == null) {
            return;
        }
        Counter.builder(DLT_PUBLICATION_TOTAL)
                .description("Reservation command records delegated to the Flash Sale DLT")
                .register(metrics)
                .increment();
    }

    private double safeSeconds(Duration value) {
        return value == null ? 0d : Math.max(0d, value.toMillis() / 1000d);
    }

    private String boundedOutcome(String outcome) {
        if (outcome == null) {
            return "unknown";
        }
        return switch (outcome) {
            case "CONFIRMED", "ALREADY_CONFIRMED", "RELEASED", "ALREADY_RELEASED",
                    "EXPIRED", "ALREADY_EXPIRED", "CONFLICT", "NOT_FOUND", "NON_CONFIRMABLE" ->
                outcome.toLowerCase(java.util.Locale.ROOT);
            default -> "other";
        };
    }

    private Timer timer(Operation operation, Outcome outcome) {
        return Timer.builder(OPERATION_DURATION)
                .description("Flash Sale runtime boundary duration")
                .tags("operation", operation.tagValue(), "dependency", operation.dependency(),
                        OUTCOME_TAG, outcome.tagValue())
                .publishPercentiles(0.50d, 0.95d, 0.99d)
                .publishPercentileHistogram()
                .register(metrics);
    }

    public enum Operation {
        HTTP_ADMISSION(FlashSaleObservationNames.RESERVATION_ADMISSION, "reservation_admission", "none"),
        CAMPAIGN_PROJECTION(FlashSaleObservationNames.CAMPAIGN_PROJECTION, "campaign_projection", "redis"),
        CAMPAIGN_RECOVERY(FlashSaleObservationNames.CAMPAIGN_RECOVERY, "campaign_recovery", "campaign"),
        REDIS_LUA(FlashSaleObservationNames.REDIS_LUA, "redis_lua", "redis"),
        REDIS_RECONCILIATION(FlashSaleObservationNames.REDIS_LUA, "redis_reconciliation", "redis"),
        REDIS_HANDOFF(FlashSaleObservationNames.REDIS_HANDOFF, "redis_handoff", "redis"),
        POSTGRES_ACCEPTANCE(FlashSaleObservationNames.POSTGRES_ACCEPTANCE, "postgres_acceptance", "postgres"),
        POSTGRES_EXPIRY(FlashSaleObservationNames.POSTGRES_EXPIRY, "postgres_expiry", "postgres"),
        OUTBOX_PUBLICATION(FlashSaleObservationNames.OUTBOX_PUBLICATION, "outbox_publication", "kafka");

        private final String observationName;
        private final String tagValue;
        private final String dependency;

        Operation(String observationName, String tagValue, String dependency) {
            this.observationName = observationName;
            this.tagValue = tagValue;
            this.dependency = dependency;
        }

        String observationName() {
            return observationName;
        }

        String tagValue() {
            return tagValue;
        }

        String dependency() {
            return dependency;
        }
    }

    private enum Outcome {
        SUCCESS("success"),
        FAILURE("failure");

        private final String tagValue;

        Outcome(String tagValue) {
            this.tagValue = tagValue;
        }

        String tagValue() {
            return tagValue;
        }
    }
}
