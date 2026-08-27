package com.philia.flashsale.order.purchasesaga.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.order.purchasesaga.domain.exception.InvalidPurchaseSagaException;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSagaStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurchaseSagaLateSuccessTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void higherVersionLateSuccessMovesCompensatedSagaToManualReview() {
        UUID orderId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID failedPayment = UUID.randomUUID();
        UUID latePayment = UUID.randomUUID();
        PurchaseSaga compensated = PurchaseSaga.start(orderId, requestId, reservationId,
                        NOW.plusSeconds(300), NOW)
                .releaseReservation(failedPayment, 1, "PROVIDER_TERMINAL_FAILURE", "CANCELLED",
                        NOW.plusSeconds(1), UUID.randomUUID(), NOW.plusSeconds(2))
                .completeRelease(NOW.plusSeconds(3));

        PurchaseSaga reviewed = compensated.enterManualReview(latePayment, 2, NOW.plusSeconds(4),
                "LATE_PAYMENT_RESERVATION_UNAVAILABLE", NOW.plusSeconds(5));

        assertThat(reviewed.status()).isEqualTo(PurchaseSagaStatus.MANUAL_REVIEW);
        assertThat(reviewed.paymentId()).isEqualTo(latePayment);
        assertThat(reviewed.lastPaymentVersion()).isEqualTo(2L);
        assertThat(reviewed.manualReviewReason()).isEqualTo("LATE_PAYMENT_RESERVATION_UNAVAILABLE");
        assertThat(reviewed.activeCommandId()).isNull();
    }

    @Test
    void sameOrLowerVersionCannotCreateAnotherManualReview() {
        PurchaseSaga compensated = PurchaseSaga.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        NOW.plusSeconds(300), NOW)
                .releaseReservation(UUID.randomUUID(), 4, "PAYMENT_DEADLINE_EXPIRED", "EXPIRED",
                        NOW.plusSeconds(1), UUID.randomUUID(), NOW.plusSeconds(2))
                .completeRelease(NOW.plusSeconds(3));

        assertThatThrownBy(() -> compensated.enterManualReview(UUID.randomUUID(), 4,
                NOW.plusSeconds(4), "LATE_PAYMENT_RESERVATION_UNAVAILABLE", NOW.plusSeconds(5)))
                .isInstanceOf(InvalidPurchaseSagaException.class)
                .hasMessageContaining("not newer");
    }
}
