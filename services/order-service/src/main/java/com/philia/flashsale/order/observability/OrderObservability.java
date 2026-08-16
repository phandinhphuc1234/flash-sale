package com.philia.flashsale.order.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Records bounded runtime observations without allowing business identifiers into metric labels.
 * The same adapter is safe to use from unit tests through its no-op fallback.
 */
@Component
public final class OrderObservability {
    public static final String OPERATION_DURATION = "order.operation.duration";
    public static final String OPERATION_TOTAL = "order.operation.total";
    public static final String OUTBOX_BACKLOG = "order.outbox.backlog";
    public static final String OUTBOX_OLDEST_PENDING_AGE = "order.outbox.oldest_pending_age_seconds";

    private static final OrderObservability NOOP = new OrderObservability();

    private final ObservationRegistry observations;
    private final MeterRegistry metrics;
    private volatile double outboxBacklog;
    private volatile double outboxOldestPendingAge;

    public OrderObservability(ObservationRegistry observations, MeterRegistry metrics) {
        this.observations = Objects.requireNonNull(observations, "observations");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        Gauge.builder(OUTBOX_BACKLOG, this, value -> value.outboxBacklog)
                .description("Number of unpublished Order outbox rows")
                .register(metrics);
        Gauge.builder(OUTBOX_OLDEST_PENDING_AGE, this, value -> value.outboxOldestPendingAge)
                .description("Age in seconds of the oldest unpublished Order outbox row")
                .register(metrics);
    }

    private OrderObservability() {
        this.observations = null;
        this.metrics = null;
    }

    public static OrderObservability noop() {
        return NOOP;
    }

    public <T> T observe(Operation operation, Supplier<T> action) {
        return observe(operation, operation.defaultStatus(), action);
    }

    public <T> T observe(Operation operation, String status, Supplier<T> action) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(action, "action");
        if (metrics == null) {
            return action.get();
        }

        String boundedStatus = boundedStatus(status);
        Timer.Sample sample = Timer.start(metrics);
        Observation observation = Observation.createNotStarted(operation.observationName(), observations)
                .lowCardinalityKeyValue("operation", operation.operationTag())
                .lowCardinalityKeyValue("event_type", operation.eventType())
                .lowCardinalityKeyValue("dependency", operation.dependency())
                .start();
        Outcome outcome = Outcome.SUCCESS;
        try (Observation.Scope ignored = observation.openScope()) {
            T result = action.get();
            increment(operation, Outcome.SUCCESS, boundedStatus);
            return result;
        } catch (RuntimeException exception) {
            outcome = Outcome.FAILURE;
            observation.error(exception);
            increment(operation, outcome, "error");
            throw exception;
        } finally {
            observation.lowCardinalityKeyValue("outcome", outcome.tagValue());
            observation.lowCardinalityKeyValue("status", boundedStatus);
            observation.stop();
            sample.stop(timer(operation, outcome, outcome == Outcome.FAILURE ? "error" : boundedStatus));
        }
    }

    public void observe(Operation operation, Runnable action) {
        observe(operation, () -> {
            action.run();
            return null;
        });
    }

    public void recordOutboxBacklog(long backlog, Duration oldestPendingAge) {
        if (metrics == null) {
            return;
        }
        outboxBacklog = Math.max(0L, backlog);
        outboxOldestPendingAge = oldestPendingAge == null
                ? 0d : Math.max(0d, oldestPendingAge.toMillis() / 1000d);
    }

    private void increment(Operation operation, Outcome outcome, String status) {
        Counter.builder(OPERATION_TOTAL)
                .description("Order runtime boundary outcomes")
                .tags("operation", operation.operationTag(), "event_type", operation.eventType(),
                        "outcome", outcome.tagValue(), "dependency", operation.dependency(),
                        "status", boundedStatus(status))
                .register(metrics)
                .increment();
    }

    private Timer timer(Operation operation, Outcome outcome, String status) {
        return Timer.builder(OPERATION_DURATION)
                .description("Order runtime boundary duration")
                .tags("operation", operation.operationTag(), "event_type", operation.eventType(),
                        "outcome", outcome.tagValue(), "dependency", operation.dependency(),
                        "status", boundedStatus(status))
                .publishPercentiles(0.50d, 0.95d, 0.99d)
                .publishPercentileHistogram()
                .register(metrics);
    }

    private String boundedStatus(String status) {
        if (status == null) {
            return "unknown";
        }
        return switch (status) {
            case "created", "event_replayed", "business_replayed", "conflict", "not_found",
                    "listed", "published", "pending", "in_progress", "down", "up", "error" -> status;
            default -> "other";
        };
    }

    public enum Operation {
        INBOUND_PROCESSING(OrderObservationNames.INBOUND_PROCESSING, "inbound_processing", "PurchaseAccepted", "kafka", "processed"),
        DURABLE_CREATION(OrderObservationNames.DURABLE_CREATION, "durable_creation", "PurchaseAccepted", "postgres", "created"),
        OWNER_DETAIL_QUERY(OrderObservationNames.OWNER_QUERY, "owner_detail_query", "OrderQuery", "postgres", "listed"),
        OWNER_LIST_QUERY(OrderObservationNames.OWNER_QUERY, "owner_list_query", "OrderQuery", "postgres", "listed"),
        OUTBOX_PUBLICATION(OrderObservationNames.OUTBOX_PUBLICATION, "outbox_publication", "OrderCreated", "kafka", "published");

        private final String observationName;
        private final String operationTag;
        private final String eventType;
        private final String dependency;
        private final String defaultStatus;

        Operation(String observationName, String operationTag, String eventType, String dependency,
                String defaultStatus) {
            this.observationName = observationName;
            this.operationTag = operationTag;
            this.eventType = eventType;
            this.dependency = dependency;
            this.defaultStatus = defaultStatus;
        }

        String observationName() { return observationName; }
        String operationTag() { return operationTag; }
        String eventType() { return eventType; }
        String dependency() { return dependency; }
        String defaultStatus() { return defaultStatus; }
    }

    private enum Outcome {
        SUCCESS("success"), FAILURE("failure");

        private final String tagValue;

        Outcome(String tagValue) { this.tagValue = tagValue; }
        String tagValue() { return tagValue; }
    }
}
