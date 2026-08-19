package com.philia.flashsale.payment.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class PaymentObservabilityTests {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void recordsStableOperationNameAndOnlyBoundedOutcomeLabel() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentObservability observability = new PaymentObservability(registry);

        observability.recordOperation(PaymentObservationNames.CHECKOUT_CREATE,
                "provider-session-cs_test_secret", Duration.ofMillis(12));
        observability.recordOutboxLag(Duration.ofSeconds(4));

        assertThat(registry.get(PaymentObservationNames.CHECKOUT_CREATE)
                .tag("outcome", "other").timer().count()).isEqualTo(1);
        assertThat(PaymentObservability.safeCategory("SUCCESS")).isEqualTo("success");
        assertThat(PaymentObservability.safeCategory("payment-id-123")).isEqualTo("other");
        assertThat(registry.get("payment.outbox.lag.seconds").gauge().value()).isEqualTo(4);
    }

    @Test
    void preservesValidW3cContextAndRestoresMdcAfterScope() {
        PaymentTraceContext context = PaymentTraceContext.fromHeaders(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", "rojo=00f067aa0ba902b7");

        try (PaymentTraceContext.Scope ignored = context.openMdc()) {
            assertThat(MDC.get(PaymentTraceContext.MDC_TRACE_ID))
                    .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(MDC.get(PaymentTraceContext.MDC_TRACEPARENT))
                    .isEqualTo(context.traceparent());
        }

        assertThat(MDC.get(PaymentTraceContext.MDC_TRACE_ID)).isNull();
        assertThat(PaymentTraceContext.isValidTraceparent("not-a-trace")).isFalse();
    }

    @Test
    void createsNewRootForAbsentOrInvalidParent() {
        PaymentTraceContext context = PaymentTraceContext.fromHeaders("bad", "raw-provider-data");

        assertThat(context.traceparent()).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        assertThat(context.tracestate()).isNull();
    }
}
