package com.philia.flashsale.campaign.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** Verifies low-cardinality Campaign metrics and safe background trace restoration. */
class CampaignObservabilityTests {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void recordsBoundedDimensionsForEachOperationalArea() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CampaignObservability observability = new CampaignObservability(registry);

        var sample = observability.start("unknown-operation");
        observability.stop(sample, "unknown-operation", "unknown-outcome");
        observability.command("unknown-command", "success");
        observability.downstream("unknown-service", "failure");
        observability.lifecycle("unknown-transition", "success");
        observability.outbox("unknown-outbox-state");
        observability.requeue("success");

        assertThat(registry.find("campaign.operation.duration").timer().getId().getTags())
                .extracting(tag -> tag.getValue())
                .contains("other")
                .doesNotContain("campaign-123", "admin@example.com", "Bearer secret");
        assertThat(registry.get("campaign.command.total").tag("command", "other").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("campaign.downstream.total").tag("service", "other").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("campaign.lifecycle.total").tag("transition", "other").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("campaign.outbox.total").tag("outcome", "other").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("campaign.outbox.requeue.total").tag("outcome", "success").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void restoresParentTraceAfterBackgroundAction() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CampaignObservability observability = new CampaignObservability(registry);
        MDC.put("traceId", "parent-trace");

        observability.withTrace("campaign-trace", () ->
                assertThat(MDC.get("traceId")).isEqualTo("campaign-trace"));

        assertThat(MDC.get("traceId")).isEqualTo("parent-trace");
    }

    @Test
    void removesTraceWhenNoStoredIdentityExists() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CampaignObservability observability = new CampaignObservability(registry);

        MDC.put("traceId", "stale-trace");
        observability.withTrace("", () -> assertThat(MDC.get("traceId")).isNull());

        assertThat(MDC.get("traceId")).isEqualTo("stale-trace");
    }
}
