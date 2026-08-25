package com.philia.flashsale.order.purchasesaga.application.port.out;

import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationConfirmedCommand;
import com.philia.flashsale.order.purchasesaga.application.result.PurchaseReservationConfirmationResult;

/** Persistence port for the atomic Order/Saga/inbox/outbox terminal transition. */
public interface ApplyPurchaseReservationConfirmationPort {
    PurchaseReservationConfirmationResult apply(PurchaseReservationConfirmedCommand command);
}
