package com.philia.flashsale.cart.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/** Verifies Cart reconciliation telemetry remains bounded and identifier-free. */
class CartObservabilityTests {

    @Test
    void reconciliationOutcomesUseOnlyTheApprovedVocabulary() {
        var registry = new SimpleMeterRegistry();
        var observability = new CartObservability(registry);

        observability.recordReconciliationOutcome("APPLIED");
        observability.recordReconciliationOutcome("partial_noop");
        observability.recordReconciliationOutcome("REPLAYED");
        observability.recordReconciliationOutcome("CONFLICT");
        observability.recordReconciliationOutcome("order-123");

        assertThat(registry.find("cart.reconciliation.outcome.total").tag("outcome", "applied").counter().count()
                + registry.find("cart.reconciliation.outcome.total").tag("outcome", "partial_noop").counter().count()
                + registry.find("cart.reconciliation.outcome.total").tag("outcome", "replayed").counter().count()
                + registry.find("cart.reconciliation.outcome.total").tag("outcome", "conflict").counter().count()
                + registry.find("cart.reconciliation.outcome.total").tag("outcome", "other").counter().count())
                .isEqualTo(5d);
        assertThat(registry.get("cart.reconciliation.outcome.total").tag("outcome", "other").counter().count())
                .isEqualTo(1d);
        assertThat(registry.get("cart.reconciliation.outcome.total").tag("outcome", "applied").counter().count())
                .isEqualTo(1d);
        assertThat(registry.get("cart.reconciliation.outcome.total").tag("outcome", "partial_noop").counter().count())
                .isEqualTo(1d);
    }

    @Test
    void dltPublicationMetricDoesNotAcceptBusinessIdentifiers() {
        var registry = new SimpleMeterRegistry();
        var observability = new CartObservability(registry);

        observability.recordDltPublication();
        observability.recordDltPublication();

        assertThat(registry.get("cart.reconciliation.dlt.publication.total").counter().count()).isEqualTo(2d);
        assertThat(registry.find("cart.reconciliation.dlt.publication.total").counter().getId().getTags()).isEmpty();
    }
}
