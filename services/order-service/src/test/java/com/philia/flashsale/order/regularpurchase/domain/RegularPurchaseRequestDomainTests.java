package com.philia.flashsale.order.regularpurchase.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.order.order.domain.model.PurchaseSource;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.regularpurchase.domain.exception.InvalidRegularPurchaseRequestException;
import com.philia.flashsale.order.regularpurchase.domain.model.IdempotencyMatch;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseLine;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequest;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequestState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegularPurchaseRequestDomainTests {
    private static final Instant RECEIVED_AT = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void cartFingerprintUsesTheCanonicalBrowserBodyAndSurvivesInternalCartBinding() {
        RegularPurchaseLine later = cartLine("00000000-0000-0000-0000-000000000020", 2, "12.0000", 12L);
        RegularPurchaseLine first = cartLine("00000000-0000-0000-0000-000000000010", 1, "10.0000", 10L);
        RegularPurchaseRequest firstRequest = cartRequest(List.of(later, first), 9L);
        RegularPurchaseRequest replay = cartRequest(List.of(first, later), 9L);

        RegularPurchaseRequest snapshotBound = firstRequest.snapshotValidated(UUID.randomUUID(), 9L,
                RECEIVED_AT.plusSeconds(1));

        assertThat(firstRequest.cartId()).isNull();
        assertThat(firstRequest.requestFingerprint()).isEqualTo(replay.requestFingerprint());
        assertThat(snapshotBound.requestFingerprint()).isEqualTo(firstRequest.requestFingerprint());
        assertThat(snapshotBound.lines()).containsExactly(first, later);
        assertThat(snapshotBound.submittedCartVersion()).isEqualTo(9L);
    }

    @Test
    void cartRequestMovesThroughIntakeAndDerivesThePaymentDeadlineFromTheFiveMinuteHold() {
        RegularPurchaseRequest request = cartRequest(List.of(cartLine(
                "00000000-0000-0000-0000-000000000010", 1, "10.0000", 8L)), 8L);
        Instant holdAcquiredAt = RECEIVED_AT.plusSeconds(3);
        Instant holdExpiresAt = holdAcquiredAt.plus(RegularPurchaseRequest.INVENTORY_HOLD_TTL);

        RegularPurchaseRequest accepted = request.snapshotValidated(UUID.randomUUID(), 8L, RECEIVED_AT.plusSeconds(1))
                .productValidated(RECEIVED_AT.plusSeconds(2))
                .holdAcquired(holdExpiresAt, holdAcquiredAt)
                .accept(request.proposedOrderId(), RECEIVED_AT.plusSeconds(4));

        assertThat(accepted.state()).isEqualTo(RegularPurchaseRequestState.ACCEPTED);
        assertThat(accepted.orderId()).isEqualTo(request.proposedOrderId());
        assertThat(accepted.holdExpiresAt()).isEqualTo(holdExpiresAt);
        assertThat(RegularPurchaseRequest.paymentDeadline(accepted.holdExpiresAt(), holdAcquiredAt))
                .isEqualTo(holdExpiresAt.minusSeconds(30));
    }

    @Test
    void scopesIdempotencyToTheAuthenticatedShopperAndRejectsConflictingCanonicalContent() {
        UUID shopperId = UUID.randomUUID();
        RegularPurchaseRequest request = RegularPurchaseRequest.receiveBuyNow(UUID.randomUUID(), shopperId, "buy-1",
                UUID.randomUUID(), UUID.randomUUID(), buyNowLine(), RECEIVED_AT);

        assertThat(request.idempotencyMatch(shopperId, "buy-1", request.requestFingerprint()))
                .isEqualTo(IdempotencyMatch.REPLAY);
        assertThat(request.idempotencyMatch(shopperId, "buy-1", "0".repeat(64)))
                .isEqualTo(IdempotencyMatch.CONFLICT);
        assertThat(request.idempotencyMatch(UUID.randomUUID(), "buy-1", request.requestFingerprint()))
                .isEqualTo(IdempotencyMatch.NOT_MATCHED);
    }

    @Test
    void preventsBusinessRejectionAfterAStockHoldAndRejectsAmbiguousCartInput() {
        RegularPurchaseRequest request = cartRequest(List.of(cartLine(
                "00000000-0000-0000-0000-000000000010", 1, "10.0000", 3L)), 3L)
                .snapshotValidated(UUID.randomUUID(), 3L, RECEIVED_AT.plusSeconds(1))
                .productValidated(RECEIVED_AT.plusSeconds(2))
                .holdAcquired(RECEIVED_AT.plusSeconds(303), RECEIVED_AT.plusSeconds(3));

        assertThatThrownBy(() -> request.reject("INSUFFICIENT_STOCK", RECEIVED_AT.plusSeconds(4)))
                .isInstanceOf(InvalidRegularPurchaseRequestException.class)
                .hasMessageContaining("stock-held");
        assertThatThrownBy(() -> RegularPurchaseRequest.receiveCart(UUID.randomUUID(), UUID.randomUUID(), "cart-1",
                UUID.randomUUID(), UUID.randomUUID(), 1L,
                List.of(new RegularPurchaseLine(UUID.randomUUID(), 1, Money.of(new BigDecimal("10.0000")), "VND", null)),
                RECEIVED_AT))
                .isInstanceOf(InvalidRegularPurchaseRequestException.class)
                .hasMessageContaining("item revisions");
        assertThatThrownBy(() -> RegularPurchaseRequest.restore(UUID.randomUUID(), UUID.randomUUID(), "cart-2",
                "0".repeat(64), PurchaseSource.CART,
                UUID.randomUUID(), UUID.randomUUID(), List.of(cartLine(
                        "00000000-0000-0000-0000-000000000020", 1, "10.0000", 1L)), 1L, null, null,
                RegularPurchaseRequestState.RECEIVED, null, null, null, RECEIVED_AT, RECEIVED_AT))
                .isInstanceOf(InvalidRegularPurchaseRequestException.class)
                .hasMessageContaining("canonical submitted body");
    }

    private static RegularPurchaseRequest cartRequest(List<RegularPurchaseLine> lines, long cartVersion) {
        return RegularPurchaseRequest.receiveCart(UUID.randomUUID(), UUID.randomUUID(), "cart-key", UUID.randomUUID(),
                UUID.randomUUID(), cartVersion, lines, RECEIVED_AT);
    }

    private static RegularPurchaseLine buyNowLine() {
        return new RegularPurchaseLine(UUID.fromString("00000000-0000-0000-0000-000000000010"), 1,
                Money.of(new BigDecimal("10.0000")), "VND", null);
    }

    private static RegularPurchaseLine cartLine(String variantId, long quantity, String price, long itemVersion) {
        return new RegularPurchaseLine(UUID.fromString(variantId), quantity, Money.of(new BigDecimal(price)), "VND",
                itemVersion);
    }
}
