package com.philia.flashsale.inventory.regularhold.application.port.in;

import com.philia.flashsale.inventory.regularhold.application.command.ConfirmRegularStockHoldCommand;
import com.philia.flashsale.inventory.regularhold.application.result.RegularStockHoldConfirmationResult;

public interface ConfirmRegularStockHoldUseCase {
    RegularStockHoldConfirmationResult confirm(ConfirmRegularStockHoldCommand command);
}
