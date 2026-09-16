package com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDownstreamException;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHold;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHoldCommand;
import com.philia.flashsale.order.regularpurchase.application.port.out.CreateRegularStockHoldPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.internal.CircuitBreakerStateMachine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ResilientInventoryRegularHoldClientAdapterTests {

    private static final String TRACE_ID = "trace-inventory-resilience";
    private static final Instant NOW = Instant.parse("2032-01-01T10:00:00Z");
    private static final UUID HOLD_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REQUEST_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SHOPPER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID VARIANT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void returnsDelegateResultAndPreservesTheExactCommandAndTrace() {
        CreateRegularStockHoldPort delegate = mock(CreateRegularStockHoldPort.class);
        CircuitBreaker breaker = circuitBreaker(2);
        RegularStockHoldCommand command = command();
        RegularStockHold expected = hold();
        when(delegate.create(command, TRACE_ID)).thenReturn(expected);

        var adapter = new ResilientInventoryRegularHoldClientAdapter(delegate, breaker);

        assertThat(adapter.create(command, TRACE_ID)).isSameAs(expected);
        verify(delegate).create(same(command), eq(TRACE_ID));
        assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void ignoresAllThreeDocumentedBusinessFailures() {
        assertIgnored(RegularPurchaseDownstreamException.Failure.INVENTORY_INSUFFICIENT_STOCK);
        assertIgnored(RegularPurchaseDownstreamException.Failure.INVENTORY_ITEM_NOT_FOUND);
        assertIgnored(RegularPurchaseDownstreamException.Failure.INVENTORY_HOLD_CONFLICT);
    }

    @Test
    void recordsAmbiguousAndUnavailableFailuresAndOpensAtTheConfiguredThreshold() {
        assertRecordedAndOpened(RegularPurchaseDownstreamException.Failure.INVENTORY_HOLD_AMBIGUOUS);
        assertRecordedAndOpened(RegularPurchaseDownstreamException.Failure.INVENTORY_SERVICE_UNAVAILABLE);
    }

    @Test
    void recordsUnexpectedOutboundRuntimeFailures() {
        CreateRegularStockHoldPort delegate = mock(CreateRegularStockHoldPort.class);
        CircuitBreaker breaker = circuitBreaker(2);
        RegularStockHoldCommand command = command();
        when(delegate.create(command, TRACE_ID)).thenThrow(new IllegalStateException("synthetic outbound failure"));
        var adapter = new ResilientInventoryRegularHoldClientAdapter(delegate, breaker);

        assertThatThrownBy(() -> adapter.create(command, TRACE_ID)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.create(command, TRACE_ID)).isInstanceOf(IllegalStateException.class);

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
        verify(delegate, times(2)).create(same(command), eq(TRACE_ID));
    }

    @Test
    void openCircuitRejectsOneHundredAttemptsWithoutCallingInventoryAndMeetsTheLatencyTarget() {
        CreateRegularStockHoldPort delegate = mock(CreateRegularStockHoldPort.class);
        CircuitBreaker breaker = circuitBreaker(2);
        RegularPurchaseDownstreamException unavailable = failure(
                RegularPurchaseDownstreamException.Failure.INVENTORY_SERVICE_UNAVAILABLE);
        when(delegate.create(command(), TRACE_ID)).thenThrow(unavailable);
        var adapter = new ResilientInventoryRegularHoldClientAdapter(delegate, breaker);

        assertThatThrownBy(() -> adapter.create(command(), TRACE_ID)).isSameAs(unavailable);
        assertThatThrownBy(() -> adapter.create(command(), TRACE_ID)).isSameAs(unavailable);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        reset(delegate);

        List<Long> elapsedNanos = new ArrayList<>();
        for (int attempt = 0; attempt < 100; attempt++) {
            long started = System.nanoTime();
            assertThatThrownBy(() -> adapter.create(command(), TRACE_ID))
                    .isInstanceOf(RegularPurchaseDownstreamException.class)
                    .extracting(exception -> ((RegularPurchaseDownstreamException) exception).failure())
                    .isEqualTo(RegularPurchaseDownstreamException.Failure.INVENTORY_SERVICE_UNAVAILABLE);
            elapsedNanos.add(System.nanoTime() - started);
        }

        elapsedNanos.sort(Long::compareTo);
        long p95Millis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos.get(94));
        long maxMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos.get(99));
        long belowTarget = elapsedNanos.stream()
                .filter(value -> value < TimeUnit.MILLISECONDS.toNanos(100))
                .count();
        System.out.printf(
                "FEATURE_050_OPEN_REJECTION attempts=100 below100ms=%d p95Ms=%d maxMs=%d delegateCalls=0%n",
                belowTarget, p95Millis, maxMillis);

        assertThat(belowTarget).isGreaterThanOrEqualTo(95);
        assertThat(p95Millis).isLessThan(100);
        verifyNoInteractions(delegate);
        assertThat(breaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(100);
    }

    @Test
    void successfulRecoveryUsesOnlyTheConfiguredHalfOpenProbesAndClosesWithoutRestart() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        CircuitBreaker breaker = recoveryCircuitBreaker(clock, 2);
        RegularStockHoldCommand command = command();
        AtomicInteger delegateCalls = new AtomicInteger();
        CountDownLatch probesEntered = new CountDownLatch(2);
        CountDownLatch releaseProbes = new CountDownLatch(1);
        CreateRegularStockHoldPort delegate = (receivedCommand, receivedTrace) -> {
            assertThat(receivedCommand).isSameAs(command);
            assertThat(receivedTrace).isEqualTo(TRACE_ID);
            int invocation = delegateCalls.incrementAndGet();
            if (invocation <= 2) {
                throw failure(RegularPurchaseDownstreamException.Failure.INVENTORY_SERVICE_UNAVAILABLE);
            }
            probesEntered.countDown();
            await(releaseProbes);
            return hold();
        };
        var adapter = new ResilientInventoryRegularHoldClientAdapter(delegate, breaker);

        open(adapter, command, breaker);
        clock.advance(Duration.ofMillis(10_001));
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RegularStockHold> firstProbe = executor.submit(() -> adapter.create(command, TRACE_ID));
            Future<RegularStockHold> secondProbe = executor.submit(() -> adapter.create(command, TRACE_ID));
            assertThat(probesEntered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

            assertUnavailable(() -> adapter.create(command, TRACE_ID));
            assertThat(delegateCalls).hasValue(4);

            releaseProbes.countDown();
            assertThat(firstProbe.get(2, TimeUnit.SECONDS)).isEqualTo(hold());
            assertThat(secondProbe.get(2, TimeUnit.SECONDS)).isEqualTo(hold());
        } finally {
            releaseProbes.countDown();
            executor.shutdownNow();
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(delegateCalls).hasValue(4);
        System.out.println(
                "FEATURE_050_RECOVERY result=CLOSED configuredProbes=2 admittedProbes=2 rejectedProbes=1");
    }

    @Test
    void failedHalfOpenProbesReopenTheCircuit() {
        MutableClock clock = new MutableClock(NOW);
        CircuitBreaker breaker = recoveryCircuitBreaker(clock, 2);
        RegularStockHoldCommand command = command();
        AtomicInteger delegateCalls = new AtomicInteger();
        CreateRegularStockHoldPort delegate = (receivedCommand, receivedTrace) -> {
            delegateCalls.incrementAndGet();
            throw failure(RegularPurchaseDownstreamException.Failure.INVENTORY_SERVICE_UNAVAILABLE);
        };
        var adapter = new ResilientInventoryRegularHoldClientAdapter(delegate, breaker);

        open(adapter, command, breaker);
        clock.advance(Duration.ofMillis(10_001));
        assertUnavailable(() -> adapter.create(command, TRACE_ID));
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertUnavailable(() -> adapter.create(command, TRACE_ID));

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(delegateCalls).hasValue(4);
    }

    @Test
    void businessRejectionDoesNotPoisonHalfOpenRecovery() {
        MutableClock clock = new MutableClock(NOW);
        CircuitBreaker breaker = recoveryCircuitBreaker(clock, 2);
        RegularStockHoldCommand command = command();
        AtomicInteger delegateCalls = new AtomicInteger();
        RegularPurchaseDownstreamException businessRejection = failure(
                RegularPurchaseDownstreamException.Failure.INVENTORY_INSUFFICIENT_STOCK);
        CreateRegularStockHoldPort delegate = (receivedCommand, receivedTrace) -> {
            int invocation = delegateCalls.incrementAndGet();
            if (invocation <= 2) {
                throw failure(RegularPurchaseDownstreamException.Failure.INVENTORY_SERVICE_UNAVAILABLE);
            }
            if (invocation == 3) {
                throw businessRejection;
            }
            return hold();
        };
        var adapter = new ResilientInventoryRegularHoldClientAdapter(delegate, breaker);

        open(adapter, command, breaker);
        clock.advance(Duration.ofMillis(10_001));
        assertThatThrownBy(() -> adapter.create(command, TRACE_ID)).isSameAs(businessRejection);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        assertThat(adapter.create(command, TRACE_ID)).isEqualTo(hold());
        assertThat(adapter.create(command, TRACE_ID)).isEqualTo(hold());

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(delegateCalls).hasValue(5);
    }

    private void assertIgnored(RegularPurchaseDownstreamException.Failure failure) {
        CreateRegularStockHoldPort delegate = mock(CreateRegularStockHoldPort.class);
        CircuitBreaker breaker = circuitBreaker(2);
        RegularStockHoldCommand command = command();
        RegularPurchaseDownstreamException rejection = failure(failure);
        when(delegate.create(command, TRACE_ID)).thenThrow(rejection);
        var adapter = new ResilientInventoryRegularHoldClientAdapter(delegate, breaker);

        for (int attempt = 0; attempt < 100; attempt++) {
            assertThatThrownBy(() -> adapter.create(command, TRACE_ID)).isSameAs(rejection);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(breaker.getMetrics().getNumberOfNotPermittedCalls()).isZero();
        verify(delegate, times(100)).create(same(command), eq(TRACE_ID));
    }

    private void assertRecordedAndOpened(RegularPurchaseDownstreamException.Failure failure) {
        CreateRegularStockHoldPort delegate = mock(CreateRegularStockHoldPort.class);
        CircuitBreaker breaker = circuitBreaker(2);
        RegularStockHoldCommand command = command();
        RegularPurchaseDownstreamException outage = failure(failure);
        when(delegate.create(command, TRACE_ID)).thenThrow(outage);
        var adapter = new ResilientInventoryRegularHoldClientAdapter(delegate, breaker);

        assertThatThrownBy(() -> adapter.create(command, TRACE_ID)).isSameAs(outage);
        assertThatThrownBy(() -> adapter.create(command, TRACE_ID)).isSameAs(outage);

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
        verify(delegate, times(2)).create(same(command), eq(TRACE_ID));
    }

    private CircuitBreaker circuitBreaker(int minimumCalls) {
        OrderInventoryResilienceProperties properties = new OrderInventoryResilienceProperties(
                minimumCalls, minimumCalls, 50.0f, Duration.ofMinutes(1), 1, false, 16);
        OrderInventoryResilienceConfiguration configuration = new OrderInventoryResilienceConfiguration();
        CircuitBreakerRegistry registry = configuration.orderInventoryCircuitBreakerRegistry(properties);
        return configuration.orderInventoryRegularHoldCircuitBreaker(registry);
    }

    private CircuitBreaker recoveryCircuitBreaker(MutableClock clock, int permittedHalfOpenCalls) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(permittedHalfOpenCalls)
                .automaticTransitionFromOpenToHalfOpenEnabled(false)
                .ignoreException(ResilientInventoryRegularHoldClientAdapter::isBusinessFailure)
                .recordException(throwable -> true)
                .build();
        return new CircuitBreakerStateMachine("orderInventoryRegularHoldRecovery", config, clock);
    }

    private void open(ResilientInventoryRegularHoldClientAdapter adapter, RegularStockHoldCommand command,
            CircuitBreaker breaker) {
        assertUnavailable(() -> adapter.create(command, TRACE_ID));
        assertUnavailable(() -> adapter.create(command, TRACE_ID));
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    private void assertUnavailable(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(RegularPurchaseDownstreamException.class)
                .extracting(exception -> ((RegularPurchaseDownstreamException) exception).failure())
                .isEqualTo(RegularPurchaseDownstreamException.Failure.INVENTORY_SERVICE_UNAVAILABLE);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for the recovery probe fixture");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("recovery probe fixture was interrupted", exception);
        }
    }

    private RegularPurchaseDownstreamException failure(RegularPurchaseDownstreamException.Failure failure) {
        return new RegularPurchaseDownstreamException(failure);
    }

    private RegularStockHoldCommand command() {
        return new RegularStockHoldCommand(HOLD_ID, REQUEST_ID, ORDER_ID, SHOPPER_ID, NOW,
                List.of(new RegularStockHoldCommand.RegularStockHoldLine(VARIANT_ID, 1)));
    }

    private RegularStockHold hold() {
        return new RegularStockHold(HOLD_ID, REQUEST_ID, ORDER_ID, "HELD", NOW.plusSeconds(300),
                List.of(new RegularStockHold.RegularStockHoldItem(VARIANT_ID, 1)));
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        private void advance(Duration duration) {
            current.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
