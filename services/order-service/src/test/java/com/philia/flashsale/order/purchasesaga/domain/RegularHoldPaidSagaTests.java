package com.philia.flashsale.order.purchasesaga.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.order.purchasesaga.domain.exception.InvalidPurchaseSagaException;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSagaStatus;
import com.philia.flashsale.order.purchasesaga.domain.model.StockParticipantType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegularHoldPaidSagaTests {
    private static final Instant CREATED = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void paidRegularHoldMovesThroughTheGenericStockConfirmationPath() {
        UUID orderId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID regularHoldId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PurchaseSaga saga = PurchaseSaga.startRegular(orderId, requestId, regularHoldId,
                CREATED.plusSeconds(300), CREATED);

        PurchaseSaga confirming = saga.confirmRegularStock(paymentId, 1, CREATED.plusSeconds(2), UUID.randomUUID(),
                CREATED.plusSeconds(3));
        PurchaseSaga completed = confirming.completeRegularStock(regularHoldId, paymentId, CREATED.plusSeconds(4));

        assertThat(saga.stockParticipantType()).isEqualTo(StockParticipantType.REGULAR_STOCK_HOLD);
        assertThat(saga.stockReferenceId()).isEqualTo(regularHoldId);
        assertThat(saga.reservationId()).isNull();
        assertThat(saga.paymentDeadline()).isEqualTo(CREATED.plusSeconds(270));
        assertThat(confirming.status()).isEqualTo(PurchaseSagaStatus.CONFIRMING_STOCK);
        assertThat(completed.status()).isEqualTo(PurchaseSagaStatus.COMPLETED);
        assertThat(completed.activeCommandId()).isNull();
        assertThat(completed.version()).isEqualTo(2);
    }

    @Test
    void keepsTheFlashSaleAndRegularStockParticipantsStrictlySeparated() {
        PurchaseSaga flashSaleSaga = PurchaseSaga.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                CREATED.plusSeconds(300), CREATED);
        PurchaseSaga regularSaga = PurchaseSaga.startRegular(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                CREATED.plusSeconds(300), CREATED);

        assertThat(flashSaleSaga.stockParticipantType()).isEqualTo(StockParticipantType.FLASH_SALE_RESERVATION);
        assertThatThrownBy(() -> flashSaleSaga.confirmRegularStock(UUID.randomUUID(), 1, CREATED.plusSeconds(1),
                UUID.randomUUID(), CREATED.plusSeconds(2)))
                .isInstanceOf(InvalidPurchaseSagaException.class)
                .hasMessageContaining("regular stock hold");
        assertThatThrownBy(() -> regularSaga.confirmReservation(UUID.randomUUID(), 1, CREATED.plusSeconds(1),
                UUID.randomUUID(), CREATED.plusSeconds(2)))
                .isInstanceOf(InvalidPurchaseSagaException.class)
                .hasMessageContaining("regular stock Saga cannot use reservation states");
    }
}
