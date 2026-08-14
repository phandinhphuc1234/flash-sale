package com.philia.flashsale.flashsale.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import com.philia.flashsale.flashsale.websupport.context.FlashSaleRequestContext;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FlashSaleObservabilityTests {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final FlashSaleObservability observability = new FlashSaleObservability(
            ObservationRegistry.create(), registry);

    @AfterEach
    void cleanUp() {
        MDC.clear();
        registry.close();
    }

    @Test
    void recordsBoundedAdmissionTimerWithPercentileSupport() {
        String result = observability.observe(FlashSaleObservability.Operation.HTTP_ADMISSION,
                () -> "accepted");

        assertThat(result).isEqualTo("accepted");
        Timer timer = registry.get(FlashSaleObservability.OPERATION_DURATION)
                .tags("operation", "reservation_admission", "dependency", "none", "outcome", "success")
                .timer();
        assertThat(timer.takeSnapshot().percentileValues())
                .extracting(value -> value.percentile())
                .containsExactlyInAnyOrder(0.50d, 0.95d, 0.99d);
        assertThat(timer.getId().getTags()).extracting(tag -> tag.getKey())
                .containsExactlyInAnyOrder("operation", "dependency", "outcome");
    }

    @Test
    void recordsFailureWithoutPuttingExceptionDetailIntoMetricTags() {
        assertThatThrownBy(() -> observability.observe(FlashSaleObservability.Operation.REDIS_LUA,
                () -> {
                    throw new IllegalStateException("idempotency-key=raw-secret");
                }))
                .isInstanceOf(IllegalStateException.class);

        Timer timer = registry.get(FlashSaleObservability.OPERATION_DURATION)
                .tags("operation", "redis_lua", "dependency", "redis", "outcome", "failure")
                .timer();
        assertThat(timer.getId().getTags()).allSatisfy(tag ->
                assertThat(tag.getValue()).doesNotContain("raw-secret"));
    }

    @Test
    void readinessRequiresRedisAndPostgresButNotOutboxBrokerPublication() {
        FlashSaleReadinessHealthIndicator indicator = new FlashSaleReadinessHealthIndicator(
                () -> true, () -> true);

        assertThatThrownBy(() -> observability.observe(FlashSaleObservability.Operation.OUTBOX_PUBLICATION,
                () -> {
                    throw new IllegalStateException("broker unavailable");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).containsEntry("requiredDependencies", java.util.List.of("postgres", "redis"));
    }

    @Test
    void readinessReturnsOnlySafeDependencyNamesWhenPostgresOrRedisIsUnavailable() {
        FlashSaleReadinessHealthIndicator indicator = new FlashSaleReadinessHealthIndicator(
                () -> {
                    throw new IllegalStateException("password=not-for-health");
                }, () -> {
                    throw new IllegalStateException("redis-password=not-for-health");
                });

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("unavailableDependencies", java.util.List.of("postgres", "redis"));
        assertThat(health.getDetails().toString()).doesNotContain("password", "not-for-health");
    }

    @Test
    void preservesHttpW3cTraceAndMdcAtTheAdmissionBoundary() throws Exception {
        String traceparent = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/flash-sales/campaign/reservations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("traceparent", traceparent);
        AtomicReference<String> traceId = new AtomicReference<>();
        AtomicReference<String> parent = new AtomicReference<>();

        new FlashSaleTraceHeaderFilter().doFilter(request, response,
                (ignoredRequest, ignoredResponse) -> {
                    traceId.set(MDC.get("traceId"));
                    parent.set((String) request.getAttribute(FlashSaleRequestContext.TRACEPARENT_ATTRIBUTE));
                });

        assertThat(traceId.get()).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(parent.get()).isEqualTo(traceparent);
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("0123456789abcdef0123456789abcdef");
    }
}
