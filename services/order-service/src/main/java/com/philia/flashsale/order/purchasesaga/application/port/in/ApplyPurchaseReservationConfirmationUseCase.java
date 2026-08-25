package com.philia.flashsale.order.purchasesaga.application.port.in;

import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationConfirmedCommand;
import com.philia.flashsale.order.purchasesaga.application.result.PurchaseReservationConfirmationResult;

/** Application entry point for the Flash Sale reservation-confirmed fact. */
public interface ApplyPurchaseReservationConfirmationUseCase {
    PurchaseReservationConfirmationResult apply(PurchaseReservationConfirmedCommand command);
}
