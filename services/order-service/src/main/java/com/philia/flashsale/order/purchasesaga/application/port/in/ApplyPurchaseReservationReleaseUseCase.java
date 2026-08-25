package com.philia.flashsale.order.purchasesaga.application.port.in;

import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationReleasedCommand;
import com.philia.flashsale.order.purchasesaga.application.result.PurchaseReservationReleaseResult;

public interface ApplyPurchaseReservationReleaseUseCase {
    PurchaseReservationReleaseResult apply(PurchaseReservationReleasedCommand command);
}
