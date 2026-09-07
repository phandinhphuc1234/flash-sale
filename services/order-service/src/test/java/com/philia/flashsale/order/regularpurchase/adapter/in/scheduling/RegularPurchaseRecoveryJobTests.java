package com.philia.flashsale.order.regularpurchase.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.philia.flashsale.order.configuration.RegularPurchaseRecoveryProperties;
import com.philia.flashsale.order.order.domain.model.PurchaseSource;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.regularpurchase.application.command.BuyNowCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.command.CartCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.model.RegularPurchaseRecoveryClaim;
import com.philia.flashsale.order.regularpurchase.application.port.out.ClaimRegularPurchaseRecoveryPort;
import com.philia.flashsale.order.regularpurchase.application.usecase.RegularPurchaseCheckoutService;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseLine;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RegularPurchaseRecoveryJobTests {
    private static final Instant NOW = Instant.parse("2032-01-01T10:00:00Z");
    private static final UUID SHOPPER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID VARIANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final RegularPurchaseCheckoutService checkout = org.mockito.Mockito.mock(RegularPurchaseCheckoutService.class);
    private final ClaimRegularPurchaseRecoveryPort claims = org.mockito.Mockito.mock(ClaimRegularPurchaseRecoveryPort.class);
    private RegularPurchaseRecoveryJob job;

    @BeforeEach
    void setUp() {
        var properties = new RegularPurchaseRecoveryProperties(Duration.ofSeconds(1), 10, Duration.ofSeconds(30));
        job = new RegularPurchaseRecoveryJob(checkout, claims, properties,
                Clock.fixed(NOW, ZoneOffset.UTC), "worker-a");
    }

    @Test
    void resumesBuyNowWithThePersistedShopperAndIdempotencyKey() {
        RegularPurchaseRequest request = request(PurchaseSource.BUY_NOW);
        when(claims.claim("worker-a", NOW, 10, Duration.ofSeconds(30)))
                .thenReturn(List.of(new RegularPurchaseRecoveryClaim(request, "worker-a", NOW.plusSeconds(30))));

        job.recoverDue();

        ArgumentCaptor<BuyNowCheckoutCommand> command = ArgumentCaptor.forClass(BuyNowCheckoutCommand.class);
        verify(checkout).checkout(command.capture());
        assertThat(command.getValue().shopperId()).isEqualTo(SHOPPER_ID);
        assertThat(command.getValue().idempotencyKey()).isEqualTo("recovery-key");
        assertThat(command.getValue().variantId()).isEqualTo(VARIANT_ID);
        verify(claims).release(request.id(), "worker-a");
    }

    @Test
    void resumesCartWithThePersistedSubmittedVersionAndItemRevision() {
        RegularPurchaseRequest request = request(PurchaseSource.CART);
        when(claims.claim(any(), any(), any(Integer.class), any(Duration.class)))
                .thenReturn(List.of(new RegularPurchaseRecoveryClaim(request, "worker-a", NOW.plusSeconds(30))));

        job.recoverDue();

        ArgumentCaptor<CartCheckoutCommand> command = ArgumentCaptor.forClass(CartCheckoutCommand.class);
        verify(checkout).checkout(command.capture());
        assertThat(command.getValue().shopperId()).isEqualTo(SHOPPER_ID);
        assertThat(command.getValue().cartVersion()).isEqualTo(7L);
        assertThat(command.getValue().lines().getFirst().itemVersion()).isEqualTo(3L);
        verify(claims).release(request.id(), "worker-a");
    }

    @Test
    void keepsLeaseWhenDownstreamFailureRequiresALaterRetry() {
        RegularPurchaseRequest request = request(PurchaseSource.BUY_NOW);
        when(claims.claim(any(), any(), any(Integer.class), any(Duration.class)))
                .thenReturn(List.of(new RegularPurchaseRecoveryClaim(request, "worker-a", NOW.plusSeconds(30))));
        doThrow(new IllegalStateException("dependency unavailable"))
                .when(checkout).checkout(any(BuyNowCheckoutCommand.class));

        job.recoverDue();

        verify(claims, never()).release(request.id(), "worker-a");
    }

    private RegularPurchaseRequest request(PurchaseSource source) {
        RegularPurchaseLine line = new RegularPurchaseLine(VARIANT_ID, 2,
                Money.of(new BigDecimal("179000.0000")), "VND", source == PurchaseSource.CART ? 3L : null);
        if (source == PurchaseSource.CART) {
            return RegularPurchaseRequest.receiveCart(UUID.randomUUID(), SHOPPER_ID, "recovery-key",
                    UUID.randomUUID(), UUID.randomUUID(), 7L, List.of(line), NOW);
        }
        return RegularPurchaseRequest.receiveBuyNow(UUID.randomUUID(), SHOPPER_ID, "recovery-key",
                UUID.randomUUID(), UUID.randomUUID(), line, NOW);
    }
}
