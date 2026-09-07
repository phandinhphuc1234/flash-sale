package com.philia.flashsale.order.regularpurchase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.order.order.application.port.out.CurrentTimePort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderIdentityPort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderNumberPort;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.regularpurchase.application.command.BuyNowCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDownstreamException;
import com.philia.flashsale.order.regularpurchase.application.model.ProductPurchaseQuote;
import com.philia.flashsale.order.regularpurchase.application.model.RegularPurchaseAcceptance;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHold;
import com.philia.flashsale.order.regularpurchase.application.port.out.CreateRegularStockHoldPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.LoadProductPurchaseQuotesPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.PersistRegularPurchasePort;
import com.philia.flashsale.order.regularpurchase.application.usecase.RegularPurchaseCheckoutService;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequest;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequestState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Crash-window tests for the resumable regular-purchase intake.
 *
 * <p>The production recovery worker/lease is intentionally not assumed here; that part belongs
 * to T087. These tests prove the durable checkpoints already exposed by the application port and
 * therefore make a future lease worker safe to build on.</p>
 */
class RegularPurchaseRecoveryIntegrationTests {
    private static final Instant NOW = Instant.parse("2032-01-01T10:00:00Z");
    private static final UUID VARIANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final PersistingIntake persistence = new PersistingIntake();
    private final LoadProductPurchaseQuotesPort quotes = org.mockito.Mockito.mock(LoadProductPurchaseQuotesPort.class);
    private final CreateRegularStockHoldPort holds = org.mockito.Mockito.mock(CreateRegularStockHoldPort.class);
    private final GenerateOrderIdentityPort identities = org.mockito.Mockito.mock(GenerateOrderIdentityPort.class);
    private final GenerateOrderNumberPort orderNumbers = org.mockito.Mockito.mock(GenerateOrderNumberPort.class);
    private final CurrentTimePort clock = org.mockito.Mockito.mock(CurrentTimePort.class);
    private RegularPurchaseCheckoutService service;

    @BeforeEach
    void setUp() {
        service = new RegularPurchaseCheckoutService(persistence, quotes, holds, identities, orderNumbers, clock);
        when(clock.now()).thenReturn(NOW);
        when(identities.generate()).thenAnswer(invocation -> UUID.randomUUID());
        when(orderNumbers.generate(any(), any())).thenReturn("REG-2032-RECOVERY");
    }

    @Test
    void failureBeforeHoldLeavesReceivedCheckpointAndASecondAttemptCanResume() {
        when(quotes.loadQuotes(any(), any())).thenThrow(new RegularPurchaseDownstreamException(
                RegularPurchaseDownstreamException.Failure.PRODUCT_SERVICE_UNAVAILABLE))
                .thenReturn(List.of(quote(VARIANT_ID)));
        when(holds.create(any(), any())).thenAnswer(invocation -> hold());

        assertThatThrownBy(() -> service.checkout(command()))
                .isInstanceOf(RegularPurchaseDownstreamException.class);
        assertThat(persistence.current.get().state()).isEqualTo(RegularPurchaseRequestState.RECEIVED);

        assertThat(service.checkout(command()).replayed()).isFalse();
        assertThat(persistence.current.get().state()).isEqualTo(RegularPurchaseRequestState.ACCEPTED);
        verify(holds).create(any(), any());
    }

    @Test
    void ambiguousHoldResponseResumesFromProductCheckpointWithTheSameHoldIdentity() {
        AtomicBoolean first = new AtomicBoolean(true);
        when(quotes.loadQuotes(any(), any())).thenReturn(List.of(quote(VARIANT_ID)));
        when(holds.create(any(), any())).thenAnswer(invocation -> {
            if (first.getAndSet(false)) {
                throw new RegularPurchaseDownstreamException(
                        RegularPurchaseDownstreamException.Failure.INVENTORY_HOLD_AMBIGUOUS);
            }
            return hold();
        });

        assertThatThrownBy(() -> service.checkout(command()))
                .isInstanceOf(RegularPurchaseDownstreamException.class);
        assertThat(persistence.current.get().state()).isEqualTo(RegularPurchaseRequestState.PRODUCT_VALIDATED);

        service.checkout(command());
        ArgumentCaptor<com.philia.flashsale.order.regularpurchase.application.model.RegularStockHoldCommand> captured =
                ArgumentCaptor.forClass(com.philia.flashsale.order.regularpurchase.application.model.RegularStockHoldCommand.class);
        verify(holds, atLeastOnce()).create(captured.capture(), any());
        assertThat(captured.getAllValues()).hasSize(2);
        assertThat(captured.getAllValues().get(0).holdId())
                .isEqualTo(captured.getAllValues().get(1).holdId());
    }

    @Test
    void crashAfterHoldBeforeAcceptResumesWithoutCallingInventoryAgain() {
        when(quotes.loadQuotes(any(), any())).thenReturn(List.of(quote(VARIANT_ID)));
        when(holds.create(any(), any())).thenAnswer(invocation -> hold());
        persistence.failNextAccept.set(true);

        assertThatThrownBy(() -> service.checkout(command())).isInstanceOf(IllegalStateException.class);
        assertThat(persistence.current.get().state()).isEqualTo(RegularPurchaseRequestState.HOLD_ACQUIRED);

        service.checkout(command());
        assertThat(persistence.current.get().state()).isEqualTo(RegularPurchaseRequestState.ACCEPTED);
        verify(holds).create(any(), any());
    }

    private BuyNowCheckoutCommand command() {
        return new BuyNowCheckoutCommand(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "recovery-key", VARIANT_ID, 1,
                Money.of(new BigDecimal("179000.0000")), "VND", "trace-recovery", null, null);
    }

    private ProductPurchaseQuote quote(UUID variantId) {
        return new ProductPurchaseQuote(variantId, true, true, null, UUID.randomUUID(), "SKU-RECOVERY",
                "Recovery Product", "Recovery Variant", Money.of(new BigDecimal("179000.0000")), "VND", 1L);
    }

    private RegularStockHold hold() {
        RegularPurchaseRequest request = persistence.current.get();
        return new RegularStockHold(request.proposedHoldId(), request.id(), request.proposedOrderId(), "HELD",
                NOW.plusSeconds(300), request.lines().stream()
                        .map(line -> new RegularStockHold.RegularStockHoldItem(line.variantId(), line.quantity()))
                        .toList());
    }

    private static final class PersistingIntake implements PersistRegularPurchasePort {
        private final AtomicReference<RegularPurchaseRequest> current = new AtomicReference<>();
        private final AtomicBoolean failNextAccept = new AtomicBoolean();

        @Override
        public Optional<RegularPurchaseRequest> findByShopperAndIdempotencyKey(UUID shopperId, String idempotencyKey) {
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
            if (failNextAccept.getAndSet(false)) {
                throw new IllegalStateException("simulated process crash before acceptance commit");
            }
            current.set(acceptance.intake());
            return acceptance.intake();
        }
    }
}
