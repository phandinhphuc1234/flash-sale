package com.philia.flashsale.inventory.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies bounded Inventory hold, outbox, DLT, and expiry metrics. */
class InventoryObservabilityTests {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final InventoryObservability observability = new InventoryObservability(registry);

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void recordsHoldStatesAndDiagnosticsWithBoundedLabels() {
        observability.recordHoldState("held");
        observability.recordHoldState("confirmed");
        observability.recordHoldState("hold-123");
        observability.recordOutboxBacklog(-4, Duration.ofSeconds(17));
        observability.recordExpiryDiagnostics(-1, Duration.ofSeconds(9));

        assertThat(registry.get(InventoryObservability.HOLD_STATE_TOTAL)
                .tag("state", "active").gauge().value()).isEqualTo(1d);
        assertThat(registry.get(InventoryObservability.HOLD_STATE_TOTAL)
                .tag("state", "confirmed").gauge().value()).isEqualTo(1d);
        assertThat(registry.get(InventoryObservability.HOLD_STATE_TOTAL)
                .tag("state", "other").gauge().value()).isEqualTo(1d);
        assertThat(registry.get(InventoryObservability.OUTBOX_BACKLOG).gauge().value())
                .isZero();
        assertThat(registry.get(InventoryObservability.OUTBOX_OLDEST_PENDING_AGE).gauge().value())
                .isEqualTo(17d);
        assertThat(registry.get(InventoryObservability.EXPIRY_DUE).gauge().value()).isZero();
        assertThat(registry.get(InventoryObservability.EXPIRY_OLDEST_DUE_AGE).gauge().value())
                .isEqualTo(9d);
    }

    @Test
    void recordsDltAndExpiryOutcomesWithoutIdentifiersOrReasons() {
        observability.recordDltPublication("commands");
        observability.recordDltPublication("topic-with-order-123");
        observability.recordExpiryOutcome("expired");
        observability.recordExpiryOutcome("private-reason");

        assertThat(registry.get(InventoryObservability.DLT_PUBLICATION_TOTAL)
                .tag("boundary", "commands").counter().count()).isEqualTo(1d);
        assertThat(registry.get(InventoryObservability.DLT_PUBLICATION_TOTAL)
                .tag("boundary", "other").counter().count()).isEqualTo(1d);
        assertThat(registry.get(InventoryObservability.EXPIRY_TOTAL)
                .tag("outcome", "expired").counter().count()).isEqualTo(1d);
        assertThat(registry.get(InventoryObservability.EXPIRY_TOTAL)
                .tag("outcome", "other").counter().count()).isEqualTo(1d);
        assertThat(registry.getMeters()).flatExtracting(meter -> meter.getId().getTags())
                .allSatisfy(tag -> assertThat(tag.getValue())
                        .doesNotContain("hold-123", "order-123", "private-reason", "secret"));
    }
}
