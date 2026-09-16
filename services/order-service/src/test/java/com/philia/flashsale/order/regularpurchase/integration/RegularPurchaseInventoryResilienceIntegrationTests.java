package com.philia.flashsale.order.regularpurchase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.philia.flashsale.order.order.application.port.out.CurrentTimePort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderIdentityPort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderNumberPort;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory.ResilientInventoryRegularHoldClientAdapter;
import com.philia.flashsale.order.regularpurchase.application.command.BuyNowCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDownstreamException;
import com.philia.flashsale.order.regularpurchase.application.model.ProductPurchaseQuote;
import com.philia.flashsale.order.regularpurchase.application.model.RegularPurchaseAcceptance;
import com.philia.flashsale.order.regularpurchase.application.port.out.CreateRegularStockHoldPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.LoadProductPurchaseQuotesPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.PersistRegularPurchasePort;
import com.philia.flashsale.order.regularpurchase.application.usecase.RegularPurchaseCheckoutService;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequest;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequestState;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegularPurchaseInventoryResilienceIntegrationTests {

    private static final Instant NOW = Instant.parse("2032-01-01T10:00:00Z");
    private static final UUID SHOPPER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID VARIANT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final TrackingPersistence persistence = new TrackingPersistence();
    private final LoadProductPurchaseQuotesPort quotes = mock(LoadProductPurchaseQuotesPort.class);
    private final CreateRegularStockHoldPort inventory = mock(CreateRegularStockHoldPort.class);
    private final GenerateOrderIdentityPort identities = mock(GenerateOrderIdentityPort.class);
    private final GenerateOrderNumberPort orderNumbers = mock(GenerateOrderNumberPort.class);
    private final CurrentTimePort clock = mock(CurrentTimePort.class);
    private CircuitBreaker circuitBreaker;
    private RegularPurchaseCheckoutService service;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of("orderInventoryRegularHoldIntegration",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .failureRateThreshold(50.0f)
                        .waitDurationInOpenState(Duration.ofMinutes(1))
                        .build());
        circuitBreaker.transitionToOpenState();
        var protectedInventory = new ResilientInventoryRegularHoldClientAdapter(inventory, circuitBreaker);
        service = new RegularPurchaseCheckoutService(
                persistence, quotes, protectedInventory, identities, orderNumbers, clock);
        when(clock.now()).thenReturn(NOW);
        when(identities.generate()).thenAnswer(invocation -> UUID.randomUUID());
        when(quotes.loadQuotes(any(), any())).thenReturn(List.of(quote()));
    }

    @Test
    void openCircuitCreatesNoFalseBusinessEffectAndLeavesTheDurableRequestRecoverable() {
        assertUnavailable();

        RegularPurchaseRequest afterFirstAttempt = persistence.current.get();
        assertThat(afterFirstAttempt.state()).isEqualTo(RegularPurchaseRequestState.PRODUCT_VALIDATED);
        assertThat(afterFirstAttempt.holdExpiresAt()).isNull();
        assertThat(afterFirstAttempt.orderId()).isNull();
        assertThat(persistence.acceptCalls).hasValue(0);
        verifyNoInteractions(inventory);

        UUID requestId = afterFirstAttempt.id();
        UUID holdId = afterFirstAttempt.proposedHoldId();
        UUID orderId = afterFirstAttempt.proposedOrderId();
        assertUnavailable();

        RegularPurchaseRequest afterReplay = persistence.current.get();
        assertThat(afterReplay.state()).isEqualTo(RegularPurchaseRequestState.PRODUCT_VALIDATED);
        assertThat(afterReplay.id()).isEqualTo(requestId);
        assertThat(afterReplay.proposedHoldId()).isEqualTo(holdId);
        assertThat(afterReplay.proposedOrderId()).isEqualTo(orderId);
        assertThat(persistence.acceptCalls).hasValue(0);
        verifyNoInteractions(inventory);
        assertThat(circuitBreaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(2);
        System.out.println(
                "FEATURE_050_DURABLE_CHECKPOINT state=PRODUCT_VALIDATED hold=false order=false payment=false acceptCalls=0");
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> service.checkout(command()))
                .isInstanceOf(RegularPurchaseDownstreamException.class)
                .extracting(exception -> ((RegularPurchaseDownstreamException) exception).failure())
                .isEqualTo(RegularPurchaseDownstreamException.Failure.INVENTORY_SERVICE_UNAVAILABLE);
    }

    private BuyNowCheckoutCommand command() {
        return new BuyNowCheckoutCommand(SHOPPER_ID, "resilience-key", VARIANT_ID, 1,
                Money.of(new BigDecimal("179000.0000")), "VND", "trace-resilience", null, null);
    }

    private ProductPurchaseQuote quote() {
        return new ProductPurchaseQuote(VARIANT_ID, true, true, null, UUID.randomUUID(), "SKU-RESILIENCE",
                "Resilience Product", "Resilience Variant", Money.of(new BigDecimal("179000.0000")),
                "VND", 1L);
    }

    private static final class TrackingPersistence implements PersistRegularPurchasePort {
        private final AtomicReference<RegularPurchaseRequest> current = new AtomicReference<>();
        private final AtomicInteger acceptCalls = new AtomicInteger();

        @Override
        public Optional<RegularPurchaseRequest> findByShopperAndIdempotencyKey(UUID shopperId,
                String idempotencyKey) {
            return Optional.ofNullable(current.get());
        }

        @Override
        public RegularPurchaseRequest register(RegularPurchaseRequest request) {
            current.compareAndSet(null, request);
            return current.get();
        }

        @Override
        public RegularPurchaseRequest update(RegularPurchaseRequest request) {
            current.set(request);
            return request;
        }

        @Override
        public RegularPurchaseRequest accept(RegularPurchaseAcceptance acceptance) {
            acceptCalls.incrementAndGet();
            current.set(acceptance.intake());
            return acceptance.intake();
        }
    }
}
