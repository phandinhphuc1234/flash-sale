package com.philia.flashsale.inventory.regularhold.application.port.in;

import com.philia.flashsale.inventory.regularhold.application.command.CreateRegularStockHoldCommand;
import com.philia.flashsale.inventory.regularhold.application.result.RegularStockHoldResult;

/** Enables the Order Service to obtain one all-or-nothing regular stock hold. */
public interface CreateRegularStockHoldUseCase {
    RegularStockHoldResult create(CreateRegularStockHoldCommand command);
}
