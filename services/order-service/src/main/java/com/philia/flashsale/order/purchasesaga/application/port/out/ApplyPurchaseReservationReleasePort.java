package com.philia.flashsale.order.purchasesaga.application.port.out;

import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationReleasedCommand;
import com.philia.flashsale.order.purchasesaga.application.result.PurchaseReservationReleaseResult;

/** Persistence port for the atomic Order/Saga/inbox/outbox release terminal transition. */
public interface ApplyPurchaseReservationReleasePort {
    PurchaseReservationReleaseResult apply(PurchaseReservationReleasedCommand command);
}
