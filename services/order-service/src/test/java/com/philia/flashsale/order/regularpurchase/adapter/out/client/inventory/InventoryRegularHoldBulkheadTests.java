package com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDownstreamException;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHold;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHoldCommand;
import com.philia.flashsale.order.regularpurchase.application.port.out.CreateRegularStockHoldPort;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class InventoryRegularHoldBulkheadTests {

    private static final String TRACE_ID = "trace-bulkhead-test";
    private static final Instant NOW = Instant.parse("2032-01-01T10:00:00Z");

    @Test
    void boundsDelegateConcurrencyRejectsExcessImmediatelyAndNeverDuplicatesExecution() throws Exception {
        int maxConcurrentCalls = 2;
        CountDownLatch admitted = new CountDownLatch(maxConcurrentCalls);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumObserved = new AtomicInteger();
        AtomicInteger delegateCalls = new AtomicInteger();
        RegularStockHoldCommand command = command();
        CreateRegularStockHoldPort delegate = (receivedCommand, receivedTraceId) -> {
            assertThat(receivedCommand).isSameAs(command);
            assertThat(receivedTraceId).isEqualTo(TRACE_ID);
            delegateCalls.incrementAndGet();
            int current = active.incrementAndGet();
            maximumObserved.accumulateAndGet(current, Math::max);
            admitted.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting for bulkhead fixture release");
                }
                return hold();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("bulkhead fixture interrupted", exception);
            } finally {
                active.decrementAndGet();
            }
        };
        CircuitBreaker circuitBreaker = CircuitBreaker.of("orderInventoryBulkheadTest",
                CircuitBreakerConfig.custom().minimumNumberOfCalls(100).slidingWindowSize(100).build());
        Bulkhead bulkhead = Bulkhead.of("orderInventoryBulkheadTest", BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrentCalls)
                .maxWaitDuration(Duration.ZERO)
                .build());
        var adapter = new ResilientInventoryRegularHoldClientAdapter(delegate, circuitBreaker, bulkhead,
                new OrderInventoryResilienceEventLogger(circuitBreaker));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RegularStockHold> first = executor.submit(() -> adapter.create(command, TRACE_ID));
            Future<RegularStockHold> second = executor.submit(() -> adapter.create(command, TRACE_ID));
            assertThat(admitted.await(2, TimeUnit.SECONDS)).isTrue();

            List<Long> rejectionNanos = new ArrayList<>();
            for (int attempt = 0; attempt < 100; attempt++) {
                long started = System.nanoTime();
                assertThatThrownBy(() -> adapter.create(command, TRACE_ID))
                        .isInstanceOf(RegularPurchaseDownstreamException.class)
                        .extracting(exception -> ((RegularPurchaseDownstreamException) exception).failure())
                        .isEqualTo(RegularPurchaseDownstreamException.Failure.INVENTORY_SERVICE_UNAVAILABLE);
                rejectionNanos.add(System.nanoTime() - started);
            }

            rejectionNanos.sort(Long::compareTo);
            long p95Millis = TimeUnit.NANOSECONDS.toMillis(rejectionNanos.get(94));
            long maxMillis = TimeUnit.NANOSECONDS.toMillis(rejectionNanos.get(99));
            long below100Ms = rejectionNanos.stream()
                    .filter(value -> value < TimeUnit.MILLISECONDS.toNanos(100))
                    .count();
            assertThat(below100Ms).isGreaterThanOrEqualTo(95);
            assertThat(delegateCalls).hasValue(maxConcurrentCalls);
            assertThat(maximumObserved).hasValue(maxConcurrentCalls);
            assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isZero();

            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo(hold());
            assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo(hold());
            assertThat(delegateCalls).hasValue(maxConcurrentCalls);
            System.out.printf(
                    "FEATURE_050_BULKHEAD maxConfigured=%d maxObserved=%d rejectedAttempts=100 below100ms=%d p95Ms=%d maxMs=%d delegateCalls=%d%n",
                    maxConcurrentCalls, maximumObserved.get(), below100Ms, p95Millis, maxMillis, delegateCalls.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
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

    private RegularStockHold hold() {
        return new RegularStockHold(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"), "HELD", NOW.plusSeconds(300),
                List.of(new RegularStockHold.RegularStockHoldItem(
                        UUID.fromString("55555555-5555-5555-5555-555555555555"), 1)));
    }
}
