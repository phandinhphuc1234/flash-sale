package com.philia.flashsale.order.purchasesaga.application.port.in;

import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldOutcomeCommand;
import com.philia.flashsale.order.purchasesaga.application.result.RegularHoldOutcomeResult;

/** Applies one released or expired Inventory regular-hold fact. */
public interface ApplyRegularHoldOutcomeUseCase {
    RegularHoldOutcomeResult apply(RegularStockHoldOutcomeCommand command);
}
