package com.philia.flashsale.order.regularpurchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.order.order.application.port.out.CurrentTimePort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderIdentityPort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderNumberPort;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.regularpurchase.application.command.BuyNowCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.model.RegularPurchaseAcceptance;
import com.philia.flashsale.order.regularpurchase.application.port.out.CreateRegularStockHoldPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.LoadProductPurchaseQuotesPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.PersistRegularPurchasePort;
import com.philia.flashsale.order.regularpurchase.application.result.RegularPurchaseCheckoutResult;
import com.philia.flashsale.order.regularpurchase.application.usecase.RegularPurchaseCheckoutService;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseLine;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Bounded duplicate-load proof: one accepted durable request has one semantic replay result. */
class RegularPurchaseReplayLoadTests {
    private static final Instant NOW = Instant.parse("2026-09-07T06:00:00Z");

    @Test
    void oneHundredDuplicateBuyNowSubmissionsDoNotCallProductOrInventoryAgain() throws Exception {
        var persistence = mock(PersistRegularPurchasePort.class);
        var quotes = mock(LoadProductPurchaseQuotesPort.class);
        var holds = mock(CreateRegularStockHoldPort.class);
        var identities = mock(GenerateOrderIdentityPort.class);
        var orderNumbers = mock(GenerateOrderNumberPort.class);
        var clock = mock(CurrentTimePort.class);
        UUID shopperId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID proposedOrderId = UUID.randomUUID();
        UUID proposedHoldId = UUID.randomUUID();
        BuyNowCheckoutCommand command = new BuyNowCheckoutCommand(shopperId, "load-replay-key", variantId, 1,
                Money.of(new BigDecimal("179000.0000")), "VND", "trace-load", null, null);
        RegularPurchaseRequest accepted = RegularPurchaseRequest.receiveBuyNow(UUID.randomUUID(), shopperId,
                command.idempotencyKey(), proposedOrderId, proposedHoldId,
                new RegularPurchaseLine(variantId, 1, command.expectedUnitPrice(), command.currency(), null), NOW)
                .productValidated(NOW).holdAcquired(NOW.plusSeconds(300), NOW).accept(proposedOrderId, NOW);
        when(clock.now()).thenReturn(NOW);
        when(identities.generate()).thenReturn(UUID.randomUUID());
        when(persistence.register(any())).thenReturn(accepted);
        var service = new RegularPurchaseCheckoutService(persistence, quotes, holds, identities, orderNumbers, clock);

        ExecutorService executor = Executors.newFixedThreadPool(20);
        try {
            List<Callable<RegularPurchaseCheckoutResult>> calls = java.util.stream.IntStream.range(0, 100)
                    .<Callable<RegularPurchaseCheckoutResult>>mapToObj(index -> () -> service.checkout(command))
                    .toList();
            List<Future<RegularPurchaseCheckoutResult>> results = executor.invokeAll(calls);

            for (Future<RegularPurchaseCheckoutResult> result : results) {
                RegularPurchaseCheckoutResult replay = result.get(10, TimeUnit.SECONDS);
                assertThat(replay.replayed()).isTrue();
                assertThat(replay.orderId()).isEqualTo(proposedOrderId);
            }
            verify(persistence, times(100)).register(any());
            verify(persistence, times(0)).accept(any(RegularPurchaseAcceptance.class));
            verify(quotes, times(0)).loadQuotes(any(), any());
            verify(holds, times(0)).create(any(), any());
        } finally {
            executor.shutdownNow();
        }
    }
}
