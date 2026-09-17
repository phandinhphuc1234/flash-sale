package com.philia.flashsale.order.regularpurchase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.philia.flashsale.order.observability.OrderReadinessHealthIndicator;
import com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory.OrderInventoryResilienceEventLogger;
import com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory.ResilientInventoryRegularHoldClientAdapter;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDownstreamException;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHoldCommand;
import com.philia.flashsale.order.regularpurchase.application.port.out.CreateRegularStockHoldPort;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Status;

class OrderInventoryResilienceObservabilityTests {

    private static final String NAME = "orderInventoryRegularHold";
    private static final Instant NOW = Instant.parse("2032-01-01T10:00:00Z");

    @Test
    void exportsOnlyNamedBoundedResilience4jMetricDimensions() {
        CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(1).slidingWindowSize(1).failureRateThreshold(100).build());
        BulkheadRegistry bulkheads = BulkheadRegistry.of(BulkheadConfig.custom()
                .maxConcurrentCalls(2).maxWaitDuration(Duration.ZERO).build());
        CircuitBreaker circuitBreaker = circuitBreakers.circuitBreaker(NAME);
        Bulkhead bulkhead = bulkheads.bulkhead(NAME);

        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        try {
            TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakers).bindTo(meters);
            TaggedBulkheadMetrics.ofBulkheadRegistry(bulkheads).bindTo(meters);
            circuitBreaker.onError(1, java.util.concurrent.TimeUnit.MILLISECONDS,
                    new IllegalStateException("remote failure"));

            assertThat(meters.getMeters()).extracting(meter -> meter.getId().getName())
                    .contains("resilience4j.circuitbreaker.calls",
                            "resilience4j.circuitbreaker.not.permitted.calls",
                            "resilience4j.circuitbreaker.state",
                            "resilience4j.bulkhead.available.concurrent.calls",
                            "resilience4j.bulkhead.max.allowed.concurrent.calls");
            assertThat(meters.get("resilience4j.circuitbreaker.state")
                    .tags("name", NAME, "state", "open").gauge().value()).isEqualTo(1d);
            assertThat(meters.get("resilience4j.bulkhead.max.allowed.concurrent.calls")
                    .tag("name", NAME).gauge().value()).isEqualTo(2d);

            Set<String> allowedTagKeys = Set.of("name", "kind", "state");
            assertThat(meters.getMeters()).flatExtracting(meter -> meter.getId().getTags())
                    .allSatisfy(tag -> {
                        assertThat(tag.getKey()).isIn(allowedTagKeys);
                        assertThat(tag.getValue()).doesNotContain(
                                "shopper", "orderId", "holdId", "purchaseRequest", "secret", "http");
                    });
        } finally {
            meters.close();
        }
    }

    @Test
    void logsBoundedTransitionsAndBothRejectionReasonsWithOnlyANormalizedTrace() {
        Logger logger = (Logger) LoggerFactory.getLogger(OrderInventoryResilienceEventLogger.class);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        AtomicInteger delegateCalls = new AtomicInteger();
        CreateRegularStockHoldPort delegate = (command, traceId) -> {
            delegateCalls.incrementAndGet();
            throw new RegularPurchaseDownstreamException(
                    RegularPurchaseDownstreamException.Failure.INVENTORY_SERVICE_UNAVAILABLE);
        };
        CircuitBreaker circuitBreaker = CircuitBreaker.of(NAME, CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(1).slidingWindowSize(1).failureRateThreshold(100).build());
        Bulkhead bulkhead = Bulkhead.of(NAME, BulkheadConfig.custom()
                .maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build());
        var adapter = new ResilientInventoryRegularHoldClientAdapter(delegate, circuitBreaker, bulkhead,
                new OrderInventoryResilienceEventLogger(circuitBreaker));
        String unsafeTrace = "secret shopper-123 hold-456";

        try {
            assertThatThrownBy(() -> adapter.create(command(), unsafeTrace))
                    .isInstanceOf(RegularPurchaseDownstreamException.class);
            assertThatThrownBy(() -> adapter.create(command(), unsafeTrace))
                    .isInstanceOf(RegularPurchaseDownstreamException.class);
            assertThat(bulkhead.tryAcquirePermission()).isTrue();
            try {
                assertThatThrownBy(() -> adapter.create(command(), unsafeTrace))
                        .isInstanceOf(RegularPurchaseDownstreamException.class);
            } finally {
                bulkhead.releasePermission();
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        List<String> messages = appender.list.stream()
                .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .toList();
        assertThat(messages).anySatisfy(message -> assertThat(message)
                .contains("stateFrom=CLOSED", "stateTo=OPEN"));
        assertThat(messages).anySatisfy(message -> assertThat(message).contains("outcome=open_circuit"));
        assertThat(messages).anySatisfy(message -> assertThat(message).contains("outcome=bulkhead_full"));
        assertThat(messages).allSatisfy(message -> assertThat(message)
                .doesNotContain("shopper-123", "hold-456", "secret"));
        assertThat(appender.list).allSatisfy(event -> assertThat(event.getLevel())
                .isIn(Level.INFO, Level.WARN));
        assertThat(delegateCalls).hasValue(1);
    }

    @Test
    void inventoryIsolationNeverParticipatesInOrderReadiness() {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults(NAME);
        circuitBreaker.transitionToOpenState();
        OrderReadinessHealthIndicator readiness = new OrderReadinessHealthIndicator(
                () -> true, () -> false, () -> 3L, () -> Duration.ofSeconds(5));

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(readiness.health().getStatus()).isEqualTo(Status.UP);
        assertThat(readiness.health().getDetails())
                .containsEntry("requiredDependencies", List.of("postgres"))
                .containsEntry("consumer", "down");
        System.out.println("FEATURE_050_READINESS inventoryCircuit=OPEN orderReadiness=UP");
    }

    private RegularStockHoldCommand command() {
        return new RegularStockHoldCommand(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"), NOW,
                List.of(new RegularStockHoldCommand.RegularStockHoldLine(
                        UUID.fromString("55555555-5555-5555-5555-555555555555"), 1)));
    }
}
