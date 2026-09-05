package com.philia.flashsale.order.purchasesaga.application.port.in;

import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldConfirmedCommand;
import com.philia.flashsale.order.purchasesaga.application.result.RegularHoldConfirmationResult;

/** Accepts one validated Inventory fact that confirms a regular stock hold. */
public interface ApplyRegularHoldConfirmationUseCase {
    RegularHoldConfirmationResult apply(RegularStockHoldConfirmedCommand command);
}
