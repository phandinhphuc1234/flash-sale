package com.philia.flashsale.order.regularpurchase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.order.order.application.port.out.CurrentTimePort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderIdentityPort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderNumberPort;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.regularpurchase.application.command.CartCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.command.CartCheckoutLine;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseBusinessException;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDownstreamException;
import com.philia.flashsale.order.regularpurchase.application.model.CartCheckoutSnapshot;
import com.philia.flashsale.order.regularpurchase.application.port.out.CreateRegularStockHoldPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.LoadCartCheckoutSnapshotPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.LoadProductPurchaseQuotesPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.PersistRegularPurchasePort;
import com.philia.flashsale.order.regularpurchase.application.usecase.RegularPurchaseCheckoutService;
import com.philia.flashsale.order.regularpurchase.domain.exception.InvalidRegularPurchaseRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Focused Cart checkout contract tests: owner/revision, currency, price, stock, and replay gates. */
class CartCheckoutUseCaseTests {
    private final PersistRegularPurchasePort persistence = mock(PersistRegularPurchasePort.class);
    private final LoadProductPurchaseQuotesPort quotes = mock(LoadProductPurchaseQuotesPort.class);
    private final CreateRegularStockHoldPort holds = mock(CreateRegularStockHoldPort.class);
    private final LoadCartCheckoutSnapshotPort snapshots = mock(LoadCartCheckoutSnapshotPort.class);
    private final GenerateOrderIdentityPort identities = mock(GenerateOrderIdentityPort.class);
    private final GenerateOrderNumberPort numbers = mock(GenerateOrderNumberPort.class);
    private final CurrentTimePort clock = mock(CurrentTimePort.class);
    private final Instant now = Instant.parse("2026-09-05T00:00:00Z");
    private RegularPurchaseCheckoutService service;

    @BeforeEach
    void setUp() {
        service = new RegularPurchaseCheckoutService(persistence, quotes, holds, snapshots, identities, numbers, clock);
        when(clock.now()).thenReturn(now);
        when(identities.generate()).thenAnswer(invocation -> UUID.randomUUID());
        when(numbers.generate(any(), any())).thenReturn("REG-20260905-TEST");
        when(persistence.register(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(persistence.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsDuplicateVariantsAndMixedCurrenciesBeforePersistence() {
        UUID variant = UUID.randomUUID();
        CartCheckoutLine first = line(variant, 1, 1, "100.00", "USD");
        CartCheckoutLine duplicate = line(variant, 2, 2, "200.00", "USD");
        CartCheckoutCommand duplicateCommand = new CartCheckoutCommand(UUID.randomUUID(), "duplicate", 1,
                List.of(first, duplicate), "trace", null, null);
        assertThatThrownBy(() -> service.checkout(duplicateCommand))
                .isInstanceOf(InvalidRegularPurchaseRequestException.class);

        CartCheckoutCommand mixedCommand = new CartCheckoutCommand(UUID.randomUUID(), "mixed", 1,
                List.of(first, line(UUID.randomUUID(), 1, 1, "100.00", "VND")), "trace", null, null);
        assertThatThrownBy(() -> service.checkout(mixedCommand))
                .isInstanceOf(InvalidRegularPurchaseRequestException.class);
        verify(persistence, never()).register(any());
    }

    @Test
    void rejectsForeignSnapshotBeforeProductOrInventory() {
        UUID shopper = UUID.randomUUID();
        CartCheckoutCommand command = command(shopper, "foreign", 2, UUID.randomUUID());
        when(snapshots.load(shopper, "trace")).thenReturn(new CartCheckoutSnapshot(UUID.randomUUID(),
                UUID.randomUUID(), 2, List.of(new CartCheckoutSnapshot.CartCheckoutSnapshotItem(
                        command.lines().getFirst().variantId(), 1, 1))));

        assertThatThrownBy(() -> service.checkout(command))
                .isInstanceOf(RegularPurchaseBusinessException.class)
                .extracting(error -> ((RegularPurchaseBusinessException) error).reason())
                .isEqualTo(RegularPurchaseBusinessException.Reason.CART_CHANGED);
        verify(quotes, never()).loadQuotes(any(), any());
        verify(holds, never()).create(any(), any());
    }

    @Test
    void rejectsPriceChangeBeforeAStockHold() {
        UUID shopper = UUID.randomUUID();
        CartCheckoutCommand command = command(shopper, "price", 2, UUID.randomUUID());
        when(snapshots.load(shopper, "trace")).thenReturn(snapshot(shopper, command));
        when(quotes.loadQuotes(any(), any())).thenReturn(List.of(
                new com.philia.flashsale.order.regularpurchase.application.model.ProductPurchaseQuote(
                        command.lines().getFirst().variantId(), true, true, null, UUID.randomUUID(), "SKU",
                        "Product", "Variant", Money.of(new BigDecimal("120.00")), "USD", 1L)));

        assertThatThrownBy(() -> service.checkout(command))
                .isInstanceOf(RegularPurchaseBusinessException.class)
                .extracting(error -> ((RegularPurchaseBusinessException) error).reason())
                .isEqualTo(RegularPurchaseBusinessException.Reason.PRICE_CHANGED);
        verify(holds, never()).create(any(), any());
    }

    @Test
    void rejectsAllLinesWhenTheAtomicHoldReportsInsufficientStock() {
        UUID shopper = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CartCheckoutCommand command = new CartCheckoutCommand(shopper, "stock", 2, List.of(
                line(first, 1, 1, "100.00", "USD"), line(second, 1, 1, "200.00", "USD")),
                "trace", null, null);
        when(snapshots.load(shopper, "trace")).thenReturn(new CartCheckoutSnapshot(UUID.randomUUID(), shopper, 2,
                List.of(new CartCheckoutSnapshot.CartCheckoutSnapshotItem(first, 1, 1),
                        new CartCheckoutSnapshot.CartCheckoutSnapshotItem(second, 1, 1))));
        when(quotes.loadQuotes(any(), any())).thenReturn(List.of(
                quote(first, "100.00"), quote(second, "200.00")));
        when(holds.create(any(), any())).thenThrow(new RegularPurchaseDownstreamException(
                RegularPurchaseDownstreamException.Failure.INVENTORY_INSUFFICIENT_STOCK));

        assertThatThrownBy(() -> service.checkout(command))
                .isInstanceOf(RegularPurchaseBusinessException.class)
                .extracting(error -> ((RegularPurchaseBusinessException) error).reason())
                .isEqualTo(RegularPurchaseBusinessException.Reason.INSUFFICIENT_STOCK);
        verify(persistence, never()).accept(any());
    }

    private CartCheckoutCommand command(UUID shopper, String key, long cartVersion, UUID variant) {
        return new CartCheckoutCommand(shopper, key, cartVersion,
                List.of(line(variant, 1, 1, "100.00", "USD")), "trace", null, null);
    }

    private CartCheckoutSnapshot snapshot(UUID shopper, CartCheckoutCommand command) {
        return new CartCheckoutSnapshot(UUID.randomUUID(), shopper, command.cartVersion(),
                command.lines().stream().map(line -> new CartCheckoutSnapshot.CartCheckoutSnapshotItem(
                        line.variantId(), line.quantity(), line.itemVersion())).toList());
    }

    private CartCheckoutLine line(UUID variant, long quantity, long version, String price, String currency) {
        return new CartCheckoutLine(variant, quantity, version, Money.of(new BigDecimal(price)), currency);
    }

    private com.philia.flashsale.order.regularpurchase.application.model.ProductPurchaseQuote quote(
            UUID variant, String price) {
        return new com.philia.flashsale.order.regularpurchase.application.model.ProductPurchaseQuote(variant, true,
                true, null, UUID.randomUUID(), "SKU", "Product", "Variant", Money.of(new BigDecimal(price)),
                "USD", 1L);
    }
}
