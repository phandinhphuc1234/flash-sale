package com.philia.flashsale.inventory.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Inventory-owned metrics for regular stock holds and their recoverable delivery boundaries.
 *
 * <p>Only fixed vocabularies are accepted as metric dimensions. Hold IDs, order IDs, variant IDs,
 * shopper IDs, trace values, raw exception messages, and Kafka payloads must never become labels.
 * The facade uses Micrometer's registry abstraction so Actuator can provide the production
 * registry while focused tests can use a simple in-memory registry.</p>
 */
@Component
public final class InventoryObservability {
    public static final String HOLD_STATE_TOTAL = "inventory.regular_hold.state.total";
    public static final String OUTBOX_BACKLOG = "inventory.regular_hold.outbox.backlog";
    public static final String OUTBOX_OLDEST_PENDING_AGE =
            "inventory.regular_hold.outbox.oldest_pending_age_seconds";
    public static final String DLT_PUBLICATION_TOTAL = "inventory.regular_hold.dlt.publication.total";
    public static final String EXPIRY_TOTAL = "inventory.regular_hold.expiry.total";
    public static final String EXPIRY_DUE = "inventory.regular_hold.expiry.due";
    public static final String EXPIRY_OLDEST_DUE_AGE =
            "inventory.regular_hold.expiry.oldest_due_age_seconds";

    private static final Set<String> HOLD_STATES = Set.of(
            "active", "expired", "confirmed", "released", "other");
    private static final Set<String> DLT_BOUNDARIES = Set.of("commands", "other");
    private static final Set<String> EXPIRY_OUTCOMES = Set.of(
            "expired", "empty", "skipped", "replayed", "error", "other");
    private static final InventoryObservability NOOP = new InventoryObservability();

    private final MeterRegistry metrics;
    private final Map<String, AtomicLong> holdStateTransitions = new ConcurrentHashMap<>();
    private final AtomicLong outboxBacklog = new AtomicLong();
    private final AtomicLong outboxOldestPendingAgeSeconds = new AtomicLong();
    private final AtomicLong expiryDue = new AtomicLong();
    private final AtomicLong expiryOldestDueAgeSeconds = new AtomicLong();

    public InventoryObservability(MeterRegistry metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        for (String state : HOLD_STATES) {
            AtomicLong count = new AtomicLong();
            holdStateTransitions.put(state, count);
            Gauge.builder(HOLD_STATE_TOTAL, count, AtomicLong::get)
                    .description("Regular stock hold lifecycle transitions by bounded state")
                    .tag("state", state)
                    .register(metrics);
        }
        Gauge.builder(OUTBOX_BACKLOG, outboxBacklog, AtomicLong::get)
                .description("Number of unpublished regular hold outbox rows")
                .register(metrics);
        Gauge.builder(OUTBOX_OLDEST_PENDING_AGE, outboxOldestPendingAgeSeconds, AtomicLong::get)
                .description("Age in seconds of the oldest unpublished regular hold outbox row")
                .register(metrics);
        Gauge.builder(EXPIRY_DUE, expiryDue, AtomicLong::get)
                .description("Number of regular stock holds observed as due by the expiry scan")
                .register(metrics);
        Gauge.builder(EXPIRY_OLDEST_DUE_AGE, expiryOldestDueAgeSeconds, AtomicLong::get)
                .description("Age in seconds of the oldest regular stock hold observed as due")
                .register(metrics);
    }

    private InventoryObservability() {
        this.metrics = null;
    }

    /** No-op fallback for direct use-case tests that do not construct a Micrometer registry. */
    public static InventoryObservability noop() {
        return NOOP;
    }

    /** Records a bounded hold lifecycle transition; it is not a business-identifier gauge. */
    public void recordHoldState(String state) {
        if (metrics == null) {
            return;
        }
        holdStateTransitions.get(boundedHoldState(state)).incrementAndGet();
    }

    /** Records the current outbox backlog and oldest pending age from a bounded diagnostics query. */
    public void recordOutboxBacklog(long backlog, Duration oldestPendingAge) {
        if (metrics == null) {
            return;
        }
        outboxBacklog.set(Math.max(0L, backlog));
        outboxOldestPendingAgeSeconds.set(nonNegativeSeconds(oldestPendingAge));
    }

    /** Records one regular-hold record routed to the consumer DLT. */
    public void recordDltPublication(String boundary) {
        if (metrics == null) {
            return;
        }
        Counter.builder(DLT_PUBLICATION_TOTAL)
                .description("Regular hold records routed to a bounded consumer DLT")
                .tag("boundary", boundedDltBoundary(boundary))
                .register(metrics)
                .increment();
    }

    /** Records one expiry scan outcome without accepting a free-form reason as a label. */
    public void recordExpiryOutcome(String outcome) {
        if (metrics == null) {
            return;
        }
        Counter.builder(EXPIRY_TOTAL)
                .description("Regular stock hold expiry scan outcomes")
                .tag("outcome", boundedExpiryOutcome(outcome))
                .register(metrics)
                .increment();
    }

    /** Records the bounded due-work diagnostics observed by the expiry worker. */
    public void recordExpiryDiagnostics(long dueCount, Duration oldestDueAge) {
        if (metrics == null) {
            return;
        }
        expiryDue.set(Math.max(0L, dueCount));
        expiryOldestDueAgeSeconds.set(nonNegativeSeconds(oldestDueAge));
    }

    private String boundedHoldState(String state) {
        if (state == null) {
            return "other";
        }
        return switch (state.trim().toLowerCase(Locale.ROOT)) {
            case "active", "held" -> "active";
            case "expired" -> "expired";
            case "confirmed" -> "confirmed";
            case "released" -> "released";
            default -> "other";
        };
    }

    private String boundedDltBoundary(String boundary) {
        if (boundary == null) {
            return "other";
        }
        String normalized = boundary.trim().toLowerCase(Locale.ROOT);
        return DLT_BOUNDARIES.contains(normalized) ? normalized : "other";
    }

    private String boundedExpiryOutcome(String outcome) {
        if (outcome == null) {
            return "other";
        }
        String normalized = outcome.trim().toLowerCase(Locale.ROOT);
        return EXPIRY_OUTCOMES.contains(normalized) ? normalized : "other";
    }

    private long nonNegativeSeconds(Duration duration) {
        return duration == null || duration.isNegative() ? 0L : duration.toSeconds();
    }
}
