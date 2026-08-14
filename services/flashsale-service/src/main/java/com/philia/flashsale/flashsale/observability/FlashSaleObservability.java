package com.philia.flashsale.flashsale.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
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
    public static final String OPERATION_DURATION = "flashsale.operation.duration";

    private static final FlashSaleObservability NOOP = new FlashSaleObservability();

    private final ObservationRegistry observations;
    private final MeterRegistry metrics;

    public FlashSaleObservability(ObservationRegistry observations, MeterRegistry metrics) {
        this.observations = Objects.requireNonNull(observations, "observations");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
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
            observation.lowCardinalityKeyValue("outcome", outcome.tagValue());
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

    private Timer timer(Operation operation, Outcome outcome) {
        return Timer.builder(OPERATION_DURATION)
                .description("Flash Sale runtime boundary duration")
                .tags("operation", operation.tagValue(), "dependency", operation.dependency(),
                        "outcome", outcome.tagValue())
                .publishPercentiles(0.50d, 0.95d, 0.99d)
                .publishPercentileHistogram()
                .register(metrics);
    }

    public enum Operation {
        HTTP_ADMISSION(FlashSaleObservationNames.RESERVATION_ADMISSION, "reservation_admission", "none"),
        CAMPAIGN_PROJECTION(FlashSaleObservationNames.CAMPAIGN_PROJECTION, "campaign_projection", "redis"),
        CAMPAIGN_RECOVERY(FlashSaleObservationNames.CAMPAIGN_RECOVERY, "campaign_recovery", "campaign"),
        REDIS_LUA(FlashSaleObservationNames.REDIS_LUA, "redis_lua", "redis"),
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
