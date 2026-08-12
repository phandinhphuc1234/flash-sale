package com.philia.flashsale.campaign.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import org.slf4j.MDC;

/**
 * Campaign's low-cardinality semantic metrics boundary.
 *
 * <p>The class depends on Micrometer's abstraction only. It never exposes a Prometheus registry,
 * identifiers, tokens, or unbounded exception text as metric tags.</p>
 */
public final class CampaignObservability {

    private static final String TRACE_ID = "traceId";
    private final MeterRegistry registry;

    public CampaignObservability(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "Meter registry is required");
    }

    /** Starts a bounded timer for a known Campaign operation. */
    public Timer.Sample start(String operation) {
        return Timer.start(registry);
    }

    /** Stops a timer with bounded operation and outcome dimensions. */
    public void stop(Timer.Sample sample, String operation, String outcome) {
        if (sample == null) {
            return;
        }
        sample.stop(registry.timer(
                "campaign.operation.duration",
                "operation", operationTag(operation),
                "outcome", outcomeTag(outcome)));
    }

    public void command(String command, String outcome) {
        increment("campaign.command.total", "command", commandTag(command), "outcome", outcomeTag(outcome));
    }

    public void downstream(String service, String outcome) {
        increment("campaign.downstream.total", "service", serviceTag(service), "outcome", outcomeTag(outcome));
    }

    public void lifecycle(String transition, String outcome) {
        increment("campaign.lifecycle.total", "transition", lifecycleTag(transition), "outcome", outcomeTag(outcome));
    }

    public void outbox(String outcome) {
        increment("campaign.outbox.total", "outcome", outboxTag(outcome));
    }

    public void requeue(String outcome) {
        increment("campaign.outbox.requeue.total", "outcome", outcomeTag(outcome));
    }

    /** Executes a background action under the stored trace identity and restores the previous MDC. */
    public void withTrace(String traceId, Runnable action) {
        Objects.requireNonNull(action, "Action is required");
        String previous = MDC.get(TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            MDC.remove(TRACE_ID);
        } else {
            MDC.put(TRACE_ID, traceId);
        }
        try {
            action.run();
        } finally {
            if (previous == null) {
                MDC.remove(TRACE_ID);
            } else {
                MDC.put(TRACE_ID, previous);
            }
        }
    }

    private void increment(String meterName, String... tags) {
        Counter.builder(meterName).tags(tags).register(registry).increment();
    }

    private String operationTag(String value) {
        return switch (safe(value)) {
            case "schedule", "activate", "end", "snapshot", "requeue" -> value;
            default -> "other";
        };
    }

    private String commandTag(String value) {
        return switch (safe(value)) {
            case "schedule", "activate", "end", "snapshot", "requeue" -> value;
            default -> "other";
        };
    }

    private String serviceTag(String value) {
        return switch (safe(value)) {
            case "product", "inventory", "authentication", "kafka", "postgres" -> value;
            default -> "other";
        };
    }

    private String lifecycleTag(String value) {
        return switch (safe(value)) {
            case "scheduled", "activated", "ended" -> value;
            default -> "other";
        };
    }

    private String outboxTag(String value) {
        return switch (safe(value)) {
            case "claimed", "published", "retry", "failed", "lease_lost", "claim_failure" -> value;
            default -> "other";
        };
    }

    private String outcomeTag(String value) {
        return switch (safe(value)) {
            case "success", "rejected", "failure", "unavailable", "duplicate", "deferred" -> value;
            default -> "other";
        };
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
