package com.philia.flashsale.order.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.order.websupport.context.OrderRequestContext;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OrderObservabilityTests {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OrderObservability observability = new OrderObservability(
            ObservationRegistry.create(), registry);

    @AfterEach
    void cleanUp() {
        MDC.clear();
        registry.close();
    }

    @Test
    void recordsOnlyBoundedObservationDimensions() {
        observability.observe(OrderObservability.Operation.INBOUND_PROCESSING,
                "created", () -> "ok");

        Timer timer = registry.get(OrderObservability.OPERATION_DURATION)
                .tags("operation", "inbound_processing", "event_type", "PurchaseAccepted",
                        "outcome", "success", "dependency", "kafka", "status", "created")
                .timer();
        assertThat(timer.takeSnapshot().percentileValues())
                .extracting(value -> value.percentile())
                .containsExactlyInAnyOrder(0.50d, 0.95d, 0.99d);
        assertThat(timer.getId().getTags()).extracting(tag -> tag.getKey())
                .containsExactlyInAnyOrder("operation", "event_type", "outcome", "dependency", "status");
        assertThat(timer.getId().getTags()).allSatisfy(tag ->
                assertThat(tag.getValue()).doesNotContain("user-1", "order-1", "secret"));
    }

    @Test
    void restoresW3cRequestContextAndClearsMdcAfterTheRequest() throws Exception {
        String traceparent = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.addHeader(OrderRequestContext.TRACEPARENT_HEADER, traceparent);
        request.addHeader(OrderRequestContext.TRACESTATE_HEADER, "vendor=value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> trace = new AtomicReference<>();
        AtomicReference<String> parent = new AtomicReference<>();

        new OrderTraceHeaderFilter().doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            trace.set(MDC.get(OrderRequestContext.TRACE_ID_MDC_KEY));
            parent.set(MDC.get(OrderRequestContext.TRACEPARENT_HEADER));
        });

        assertThat(trace.get()).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(parent.get()).isEqualTo(traceparent);
        assertThat(response.getHeader(OrderRequestContext.TRACE_HEADER))
                .isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(MDC.get(OrderRequestContext.TRACE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(OrderRequestContext.TRACEPARENT_HEADER)).isNull();
    }

    @Test
    void readinessStaysUpForKafkaOutageButFailsWhenPostgresIsUnavailable() {
        OrderReadinessHealthIndicator kafkaDown = new OrderReadinessHealthIndicator(
                () -> true, () -> false, () -> 4L, () -> java.time.Duration.ofSeconds(12));
        assertThat(kafkaDown.health().getStatus()).isEqualTo(Status.UP);
        assertThat(kafkaDown.health().getDetails())
                .containsEntry("consumer", "down")
                .containsEntry("outboxBacklog", 4L)
                .containsEntry("outboxOldestPendingAgeSeconds", 12.0d);

        OrderReadinessHealthIndicator postgresDown = new OrderReadinessHealthIndicator(
                () -> false, () -> true);
        assertThat(postgresDown.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(postgresDown.health().getDetails())
                .containsEntry("unavailableDependencies", java.util.List.of("postgres"));
    }

    @Test
    void recordsSagaStateAgeConsumerOutcomesAndDltWithoutBusinessIdentifiers() {
        observability.recordSagaDiagnostics(Map.of("PAYMENT_PENDING", 3L, "MANUAL_REVIEW", 1L),
                Duration.ofSeconds(45));
        observability.recordConsumerOutcome(OrderObservability.ConsumerBoundary.PAYMENT_RESULTS,
                "MANUAL_REVIEW");
        observability.recordDltPublication(OrderObservability.ConsumerBoundary.RESERVATION_RESULTS);

        assertThat(registry.get(OrderObservability.SAGA_STATE)
                .tag("status", "PAYMENT_PENDING").gauge().value()).isEqualTo(3d);
        assertThat(registry.get(OrderObservability.SAGA_STATE)
                .tag("status", "MANUAL_REVIEW").gauge().value()).isEqualTo(1d);
        assertThat(registry.get(OrderObservability.SAGA_OLDEST_STEP_AGE).gauge().value())
                .isEqualTo(45d);
        assertThat(registry.get(OrderObservability.CONSUMER_OUTCOME_TOTAL)
                .tags("consumer", "payment_results", "outcome", "manual_review")
                .counter().count()).isEqualTo(1d);
        assertThat(registry.get(OrderObservability.DLT_PUBLICATION_TOTAL)
                .tag("consumer", "reservation_results").counter().count()).isEqualTo(1d);
        assertThat(registry.getMeters()).flatExtracting(meter -> meter.getId().getTags())
                .allSatisfy(tag -> assertThat(tag.getValue()).doesNotContain("orderId", "sagaId", "secret"));
    }

    @Test
    void recordsRegularIntakeRecoverySagaAndManualReviewWithBoundedDimensions() {
        observability.recordRegularIntake("buy-now", "accepted");
        observability.recordRegularIntake("shopper-123", "private-reason");
        observability.recordRegularRecovery("checkout", "deferred");
        observability.recordRegularRecovery("request-123", "private-reason");
        observability.recordSagaTransition("PAYMENT_PENDING", "MANUAL_REVIEW", "MANUAL_REVIEW");
        observability.recordManualReview("late_success");

        assertThat(registry.get(OrderObservability.REGULAR_INTAKE_TOTAL)
                .tags("source", "buy_now", "outcome", "accepted").counter().count()).isEqualTo(1d);
        assertThat(registry.get(OrderObservability.REGULAR_INTAKE_TOTAL)
                .tags("source", "other", "outcome", "other").counter().count()).isEqualTo(1d);
        assertThat(registry.get(OrderObservability.REGULAR_RECOVERY_TOTAL)
                .tags("stage", "checkout", "outcome", "deferred").counter().count()).isEqualTo(1d);
        assertThat(registry.get(OrderObservability.SAGA_TRANSITION_TOTAL)
                .tags("from", "PAYMENT_PENDING", "to", "MANUAL_REVIEW", "outcome", "manual_review")
                .counter().count()).isEqualTo(1d);
        assertThat(registry.get(OrderObservability.MANUAL_REVIEW_TOTAL)
                .tag("reason", "late_success").counter().count()).isEqualTo(1d);
        assertThat(registry.getMeters()).flatExtracting(meter -> meter.getId().getTags())
                .allSatisfy(tag -> assertThat(tag.getValue())
                        .doesNotContain("shopper-123", "request-123", "private-reason"));
    }
}
