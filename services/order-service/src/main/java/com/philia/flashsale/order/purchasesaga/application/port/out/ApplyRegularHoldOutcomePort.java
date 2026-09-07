package com.philia.flashsale.order.purchasesaga.application.port.out;

import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldOutcomeCommand;
import com.philia.flashsale.order.purchasesaga.application.result.RegularHoldOutcomeResult;

/** Atomic persistence boundary for released/expired regular-hold facts. */
public interface ApplyRegularHoldOutcomePort {
    RegularHoldOutcomeResult apply(RegularStockHoldOutcomeCommand command);
}
