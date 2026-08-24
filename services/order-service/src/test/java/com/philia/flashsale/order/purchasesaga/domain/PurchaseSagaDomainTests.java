package com.philia.flashsale.order.purchasesaga.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.order.purchasesaga.domain.exception.InvalidPurchaseSagaException;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSagaStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurchaseSagaDomainTests {
    private static final Instant CREATED = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void startsWithStablePurchaseRequestIdentityAndThirtySecondSafetyMargin() {
        UUID purchaseRequestId = UUID.randomUUID();
        PurchaseSaga saga = PurchaseSaga.start(UUID.randomUUID(), purchaseRequestId, UUID.randomUUID(),
                CREATED.plusSeconds(300), CREATED);

        assertThat(saga.id()).isEqualTo(purchaseRequestId);
        assertThat(saga.status()).isEqualTo(PurchaseSagaStatus.PAYMENT_PENDING);
        assertThat(saga.version()).isZero();
        assertThat(saga.paymentDeadline()).isEqualTo(CREATED.plusSeconds(270));
    }

    @Test
    void rejectsAReservationWithoutAFuturePaymentWindow() {
        assertThatThrownBy(() -> PurchaseSaga.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                CREATED.plusSeconds(30), CREATED))
                .isInstanceOf(InvalidPurchaseSagaException.class)
                .hasMessageContaining("future payment window");
    }

    @Test
    void keepsOrderIdentitySeparateFromStableSagaIdentity() {
        UUID orderId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        PurchaseSaga saga = PurchaseSaga.start(orderId, purchaseRequestId, UUID.randomUUID(),
                CREATED.plusSeconds(300), CREATED);

        assertThat(saga.orderId()).isEqualTo(orderId);
        assertThat(saga.id()).isEqualTo(saga.purchaseRequestId()).isEqualTo(purchaseRequestId);
    }
}
