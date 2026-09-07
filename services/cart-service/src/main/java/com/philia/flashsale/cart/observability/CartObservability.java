package com.philia.flashsale.cart.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Cart-owned telemetry facade for asynchronous checkout reconciliation.
 *
 * <p>Only fixed outcome vocabularies are accepted as labels. Cart IDs, Order IDs, shopper IDs,
 * exception messages, and message payloads are deliberately excluded from metrics.
 */
@Component
public final class CartObservability {
    public static final String RECONCILIATION_OUTCOME_TOTAL = "cart.reconciliation.outcome.total";
    public static final String RECONCILIATION_DLT_PUBLICATION_TOTAL = "cart.reconciliation.dlt.publication.total";

    private static final Set<String> OUTCOMES = Set.of("applied", "partial_noop", "replayed", "conflict", "other");
    private static final CartObservability NOOP = new CartObservability();

    private final MeterRegistry metrics;

    @Autowired
    public CartObservability(MeterRegistry metrics) {
        this.metrics = metrics;
    }

    private CartObservability() {
        this.metrics = null;
    }

    public static CartObservability noop() {
        return NOOP;
    }

    public void recordReconciliationOutcome(String outcome) {
        if (metrics == null) {
            return;
        }
        Counter.builder(RECONCILIATION_OUTCOME_TOTAL)
                .description("Cart purchased-snapshot reconciliation outcomes")
                .tag("outcome", boundedOutcome(outcome))
                .register(metrics)
                .increment();
    }

    public void recordDltPublication() {
        if (metrics == null) {
            return;
        }
        Counter.builder(RECONCILIATION_DLT_PUBLICATION_TOTAL)
                .description("Cart reconciliation records published to the DLT")
                .register(metrics)
                .increment();
    }

    private String boundedOutcome(String outcome) {
        if (outcome == null) {
            return "other";
        }
        String normalized = outcome.trim().toLowerCase(Locale.ROOT);
        return OUTCOMES.contains(normalized) ? normalized : "other";
    }
}
