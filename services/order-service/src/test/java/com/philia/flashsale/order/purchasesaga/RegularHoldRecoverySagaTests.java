package com.philia.flashsale.order.purchasesaga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.order.purchasesaga.domain.exception.InvalidPurchaseSagaException;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSagaStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Focused recovery contract for a regular-stock Order Saga.
 *
 * <p>The test intentionally exercises the domain transition boundary rather than a database or
 * Kafka adapter. Adapter/integration suites verify persistence and message envelopes separately;
 * this class makes the monotonic, success-dominant recovery rules easy to review.</p>
 */
class RegularHoldRecoverySagaTests {
    private static final Instant CREATED = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void providerFailureReleasesTheRegularHoldAndCancelsTheOrder() {
        PurchaseSaga releasing = startRegular()
                .releaseRegularStock(UUID.randomUUID(), 1, "PROVIDER_TERMINAL_FAILURE", "CANCELLED",
                        CREATED.plusSeconds(1), UUID.randomUUID(), CREATED.plusSeconds(2));

        PurchaseSaga compensated = releasing.completeRegularStockRelease(CREATED.plusSeconds(3));

        assertThat(releasing.status()).isEqualTo(PurchaseSagaStatus.RELEASING_STOCK);
        assertThat(releasing.desiredOrderStatus()).isEqualTo("CANCELLED");
        assertThat(compensated.status()).isEqualTo(PurchaseSagaStatus.COMPENSATED);
        assertThat(compensated.activeCommandId()).isNull();
    }

    @Test
    void paymentDeadlineFailureReleasesTheRegularHoldAndExpiresTheOrder() {
        PurchaseSaga releasing = startRegular()
                .releaseRegularStock(UUID.randomUUID(), 2, "PAYMENT_DEADLINE_EXPIRED", "EXPIRED",
                        CREATED.plusSeconds(1), UUID.randomUUID(), CREATED.plusSeconds(2));

        assertThat(releasing.desiredOrderStatus()).isEqualTo("EXPIRED");
        assertThat(releasing.completeRegularStockRelease(CREATED.plusSeconds(3)).status())
                .isEqualTo(PurchaseSagaStatus.COMPENSATED);
    }

    @Test
    void inventoryExpiryClosesAStillPendingRegularPurchaseAsExpired() {
        PurchaseSaga expired = startRegular().completeRegularStockExpiry(CREATED.plusSeconds(301));

        assertThat(expired.status()).isEqualTo(PurchaseSagaStatus.COMPENSATED);
        assertThat(expired.desiredOrderStatus()).isEqualTo("EXPIRED");
        assertThat(expired.version()).isEqualTo(1);
        assertThat(expired.activeCommandId()).isNull();
    }

    @Test
    void inventoryExpiryWinsOverAnInFlightReleaseAndForcesExpiredTerminalStatus() {
        PurchaseSaga releasing = startRegular()
                .releaseRegularStock(UUID.randomUUID(), 1, "PROVIDER_TERMINAL_FAILURE", "CANCELLED",
                        CREATED.plusSeconds(1), UUID.randomUUID(), CREATED.plusSeconds(2));

        PurchaseSaga expired = releasing.completeRegularStockExpiry(CREATED.plusSeconds(301));

        assertThat(expired.status()).isEqualTo(PurchaseSagaStatus.COMPENSATED);
        assertThat(expired.desiredOrderStatus()).isEqualTo("EXPIRED");
        assertThat(expired.version()).isEqualTo(releasing.version() + 1);
    }

    @Test
    void staleOrReorderedPaymentVersionCannotAdvanceTheSaga() {
        UUID orderId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        PurchaseSaga recordedVersion = PurchaseSaga.restoreRegular(requestId, orderId, requestId, holdId,
                PurchaseSagaStatus.PAYMENT_PENDING, CREATED.plusSeconds(270), UUID.randomUUID(), 4L,
                CREATED.plusSeconds(1), null, null, null, null, CREATED.plusSeconds(1), 1,
                CREATED, CREATED.plusSeconds(1));

        assertThatThrownBy(() -> recordedVersion.confirmRegularStock(UUID.randomUUID(), 3,
                CREATED.plusSeconds(3), UUID.randomUUID(), CREATED.plusSeconds(4)))
                .isInstanceOf(InvalidPurchaseSagaException.class)
                .hasMessageContaining("payment version regressed");

        assertThatThrownBy(() -> recordedVersion.releaseReservation(UUID.randomUUID(), 2,
                "PROVIDER_TERMINAL_FAILURE", "CANCELLED", CREATED.plusSeconds(3),
                UUID.randomUUID(), CREATED.plusSeconds(4)))
                .isInstanceOf(InvalidPurchaseSagaException.class)
                .hasMessageContaining("payment version regressed");
    }

    @Test
    void higherVersionLateSuccessDominatesCompensationButRequiresManualReview() {
        UUID failedPayment = UUID.randomUUID();
        PurchaseSaga compensated = startRegular()
                .releaseRegularStock(failedPayment, 1, "PROVIDER_TERMINAL_FAILURE", "CANCELLED",
                        CREATED.plusSeconds(1), UUID.randomUUID(), CREATED.plusSeconds(2))
                .completeRegularStockRelease(CREATED.plusSeconds(3));

        PurchaseSaga reviewed = compensated.enterManualReview(UUID.randomUUID(), 2,
                CREATED.plusSeconds(4), "LATE_PAYMENT_RESERVATION_UNAVAILABLE", CREATED.plusSeconds(5));

        assertThat(reviewed.status()).isEqualTo(PurchaseSagaStatus.MANUAL_REVIEW);
        assertThat(reviewed.lastPaymentVersion()).isEqualTo(2L);
        assertThat(reviewed.manualReviewReason()).isEqualTo("LATE_PAYMENT_RESERVATION_UNAVAILABLE");
        assertThat(reviewed.activeCommandId()).isNull();
    }

    @Test
    void releasedResultAfterSuccessInFlightMovesToManualReview() {
        PurchaseSaga confirming = startRegular().confirmRegularStock(UUID.randomUUID(), 1,
                CREATED.plusSeconds(1), UUID.randomUUID(), CREATED.plusSeconds(2));

        PurchaseSaga reviewed = confirming.enterManualReviewAfterReservationFailure(CREATED.plusSeconds(3));

        assertThat(reviewed.status()).isEqualTo(PurchaseSagaStatus.MANUAL_REVIEW);
        assertThat(reviewed.manualReviewReason()).isEqualTo("LATE_PAYMENT_RESERVATION_UNAVAILABLE");
        assertThat(reviewed.lastPaymentVersion()).isEqualTo(1L);
    }

    @Test
    void duplicateOrSameVersionManualReviewIsRejected() {
        PurchaseSaga compensated = startRegular()
                .releaseRegularStock(UUID.randomUUID(), 4, "PAYMENT_DEADLINE_EXPIRED", "EXPIRED",
                        CREATED.plusSeconds(1), UUID.randomUUID(), CREATED.plusSeconds(2))
                .completeRegularStockRelease(CREATED.plusSeconds(3));

        assertThatThrownBy(() -> compensated.enterManualReview(UUID.randomUUID(), 4,
                CREATED.plusSeconds(4), "LATE_PAYMENT_RESERVATION_UNAVAILABLE", CREATED.plusSeconds(5)))
                .isInstanceOf(InvalidPurchaseSagaException.class)
                .hasMessageContaining("not newer");
    }

    private PurchaseSaga startRegular() {
        return PurchaseSaga.startRegular(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                CREATED.plusSeconds(300), CREATED);
    }
}
