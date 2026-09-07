package com.philia.flashsale.order.purchasesaga.application.port.out;

import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldConfirmedCommand;
import com.philia.flashsale.order.purchasesaga.application.result.RegularHoldConfirmationResult;

/** Atomic persistence capability for regular stock confirmation, Order terminalization, inbox, and outbox. */
public interface ApplyRegularHoldConfirmationPort {
    RegularHoldConfirmationResult apply(RegularStockHoldConfirmedCommand command);
}
