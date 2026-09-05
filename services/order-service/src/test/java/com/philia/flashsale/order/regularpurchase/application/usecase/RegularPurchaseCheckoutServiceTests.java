package com.philia.flashsale.order.regularpurchase.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.order.order.application.port.out.CurrentTimePort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderIdentityPort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderNumberPort;
import com.philia.flashsale.order.order.domain.model.PurchaseSource;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.regularpurchase.application.command.BuyNowCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.command.CartCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.command.CartCheckoutLine;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseBusinessException;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDownstreamException;
import com.philia.flashsale.order.regularpurchase.application.model.ProductPurchaseQuote;
import com.philia.flashsale.order.regularpurchase.application.model.CartCheckoutSnapshot;
import com.philia.flashsale.order.regularpurchase.application.model.RegularPurchaseAcceptance;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHold;
import com.philia.flashsale.order.regularpurchase.application.port.out.CreateRegularStockHoldPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.LoadCartCheckoutSnapshotPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.LoadProductPurchaseQuotesPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.PersistRegularPurchasePort;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseLine;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Application tests prove checkpoints surround, rather than contain, downstream HTTP decisions. */
class RegularPurchaseCheckoutServiceTests {

    private final PersistRegularPurchasePort persistence = mock(PersistRegularPurchasePort.class);
    private final LoadProductPurchaseQuotesPort quotes = mock(LoadProductPurchaseQuotesPort.class);
    private final CreateRegularStockHoldPort holds = mock(CreateRegularStockHoldPort.class);
    private final LoadCartCheckoutSnapshotPort cartSnapshots = mock(LoadCartCheckoutSnapshotPort.class);
    private final GenerateOrderIdentityPort identities = mock(GenerateOrderIdentityPort.class);
    private final GenerateOrderNumberPort orderNumbers = mock(GenerateOrderNumberPort.class);
    private final CurrentTimePort clock = mock(CurrentTimePort.class);
    private final Instant now = Instant.parse("2026-09-04T06:00:00Z");
    private RegularPurchaseCheckoutService service;

    @BeforeEach
    void setUp() {
        service = new RegularPurchaseCheckoutService(persistence, quotes, holds, cartSnapshots, identities,
                orderNumbers, clock);
        when(clock.now()).thenReturn(now);
        when(identities.generate()).thenAnswer(invocation -> UUID.randomUUID());
        when(orderNumbers.generate(any(), any())).thenReturn("FS-20260904-TEST");
        when(persistence.register(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(persistence.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(persistence.accept(any())).thenAnswer(invocation ->
                ((RegularPurchaseAcceptance) invocation.getArgument(0)).intake());
    }

    @Test
    void persistsCheckpointsAroundProductAndInventoryThenAtomicallyAccepts() {
        BuyNowCheckoutCommand command = command();
        when(quotes.loadQuotes(any(), any())).thenAnswer(invocation -> {
            UUID variantId = invocation.<List<UUID>>getArgument(0).getFirst();
            return List.of(quote(variantId, "179000.0000"));
        });
        when(holds.create(any(), any())).thenAnswer(invocation -> {
            var holdCommand = invocation.<com.philia.flashsale.order.regularpurchase.application.model.RegularStockHoldCommand>
                    getArgument(0);
            return new RegularStockHold(holdCommand.holdId(), holdCommand.purchaseRequestId(), holdCommand.orderId(),
                    "HELD", now.plusSeconds(300), holdCommand.items().stream()
                            .map(item -> new RegularStockHold.RegularStockHoldItem(item.variantId(), item.quantity()))
                            .toList());
        });

        var result = service.checkout(command);

        ArgumentCaptor<RegularPurchaseAcceptance> acceptance =
                ArgumentCaptor.forClass(RegularPurchaseAcceptance.class);
        verify(persistence).accept(acceptance.capture());
        assertThat(result.source()).isEqualTo(PurchaseSource.BUY_NOW);
        assertThat(result.replayed()).isFalse();
        assertThat(result.totalAmount().amount()).isEqualByComparingTo("179000.0000");
        assertThat(result.paymentDeadline()).isEqualTo(now.plusSeconds(270));
        assertThat(acceptance.getValue().order().stockReferenceId())
                .isEqualTo(acceptance.getValue().intake().proposedHoldId());
        verify(persistence, org.mockito.Mockito.times(2)).update(any());
        // PRODUCT_VALIDATED then HOLD_ACQUIRED are durable checkpoints.
    }

    @Test
    void replaysAnAcceptedRequestWithoutCallingProductOrInventoryAgain() {
        BuyNowCheckoutCommand command = command();
        UUID proposedOrderId = UUID.randomUUID();
        RegularPurchaseRequest accepted = RegularPurchaseRequest.receiveBuyNow(UUID.randomUUID(), command.shopperId(),
                command.idempotencyKey(), proposedOrderId, UUID.randomUUID(),
                new RegularPurchaseLine(command.variantId(), command.quantity(), command.expectedUnitPrice(),
                        command.currency(), null), now)
                .productValidated(now).holdAcquired(now.plusSeconds(300), now).accept(
                        proposedOrderId, now);
        when(persistence.register(any())).thenReturn(accepted);

        var result = service.checkout(command);

        assertThat(result.replayed()).isTrue();
        verify(quotes, never()).loadQuotes(any(), any());
        verify(holds, never()).create(any(), any());
        verify(persistence, never()).accept(any());
    }

    @Test
    void recordsPriceChangedBeforeAnyHoldIsRequested() {
        when(quotes.loadQuotes(any(), any())).thenAnswer(invocation -> List.of(quote(
                invocation.<List<UUID>>getArgument(0).getFirst(), "189000.0000")));

        assertThatThrownBy(() -> service.checkout(command()))
                .isInstanceOf(RegularPurchaseBusinessException.class)
                .extracting(exception -> ((RegularPurchaseBusinessException) exception).reason())
                .isEqualTo(RegularPurchaseBusinessException.Reason.PRICE_CHANGED);

        verify(holds, never()).create(any(), any());
        verify(persistence).update(any());
    }

    @Test
    void recordsInsufficientStockButLeavesTimeoutsForSameIdentityRecovery() {
        when(quotes.loadQuotes(any(), any())).thenAnswer(invocation -> List.of(quote(
                invocation.<List<UUID>>getArgument(0).getFirst(), "179000.0000")));
        when(holds.create(any(), any())).thenThrow(new RegularPurchaseDownstreamException(
                RegularPurchaseDownstreamException.Failure.INVENTORY_INSUFFICIENT_STOCK));

        assertThatThrownBy(() -> service.checkout(command()))
                .isInstanceOf(RegularPurchaseBusinessException.class)
                .extracting(exception -> ((RegularPurchaseBusinessException) exception).reason())
                .isEqualTo(RegularPurchaseBusinessException.Reason.INSUFFICIENT_STOCK);
        verify(persistence, org.mockito.Mockito.times(2)).update(any());
    }

    @Test
    void cartCheckoutComparesOwnerRevisionAndCreatesOneCartOrder() {
        UUID shopper = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        var command = new CartCheckoutCommand(shopper, "cart-key", 7, List.of(
                new CartCheckoutLine(first, 1, 3, Money.of(new BigDecimal("179000.0000")), "VND"),
                new CartCheckoutLine(second, 2, 5, Money.of(new BigDecimal("99000.0000")), "VND")),
                "trace-cart", null, null);
        when(cartSnapshots.load(shopper, "trace-cart")).thenReturn(new CartCheckoutSnapshot(cartId, shopper, 7,
                List.of(new CartCheckoutSnapshot.CartCheckoutSnapshotItem(first, 1, 3),
                        new CartCheckoutSnapshot.CartCheckoutSnapshotItem(second, 2, 5))));
        when(quotes.loadQuotes(any(), any())).thenReturn(List.of(quote(first, "179000.0000"),
                quote(second, "99000.0000")));
        when(holds.create(any(), any())).thenAnswer(invocation -> {
            var holdCommand = invocation.<com.philia.flashsale.order.regularpurchase.application.model.RegularStockHoldCommand>
                    getArgument(0);
            return new RegularStockHold(holdCommand.holdId(), holdCommand.purchaseRequestId(), holdCommand.orderId(),
                    "HELD", now.plusSeconds(300), holdCommand.items().stream()
                            .map(item -> new RegularStockHold.RegularStockHoldItem(item.variantId(), item.quantity()))
                            .toList());
        });

        var result = service.checkout(command);

        assertThat(result.source()).isEqualTo(PurchaseSource.CART);
        assertThat(result.totalAmount().amount()).isEqualByComparingTo("377000.0000");
        ArgumentCaptor<RegularPurchaseAcceptance> acceptance = ArgumentCaptor.forClass(RegularPurchaseAcceptance.class);
        verify(persistence).accept(acceptance.capture());
        assertThat(acceptance.getValue().order().purchaseSource()).isEqualTo(PurchaseSource.CART);
        assertThat(acceptance.getValue().order().cartId()).isEqualTo(cartId);
        assertThat(acceptance.getValue().order().lines()).hasSize(2);
    }

    @Test
    void cartMutationBeforeCheckoutRejectsBeforeProductOrInventory() {
        UUID shopper = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        var command = new CartCheckoutCommand(shopper, "cart-changed", 3, List.of(
                new CartCheckoutLine(variant, 1, 2, Money.of(new BigDecimal("179000.0000")), "VND")),
                "trace-cart-changed", null, null);
        when(cartSnapshots.load(shopper, "trace-cart-changed")).thenReturn(new CartCheckoutSnapshot(
                UUID.randomUUID(), shopper, 4, List.of(
                        new CartCheckoutSnapshot.CartCheckoutSnapshotItem(variant, 1, 2))));

        assertThatThrownBy(() -> service.checkout(command))
                .isInstanceOf(RegularPurchaseBusinessException.class)
                .extracting(exception -> ((RegularPurchaseBusinessException) exception).reason())
                .isEqualTo(RegularPurchaseBusinessException.Reason.CART_CHANGED);
        verify(quotes, never()).loadQuotes(any(), any());
        verify(holds, never()).create(any(), any());
    }

    private BuyNowCheckoutCommand command() {
        return new BuyNowCheckoutCommand(UUID.randomUUID(), "buy-now-key", UUID.randomUUID(), 1,
                Money.of(new BigDecimal("179000.0000")), "VND", "trace-49", null, null);
    }

    private ProductPurchaseQuote quote(UUID variantId, String unitPrice) {
        return new ProductPurchaseQuote(variantId, true, true, null, UUID.randomUUID(), "SKU-1", "Product",
                "Variant", Money.of(new BigDecimal(unitPrice)), "VND", 1L);
    }
}
