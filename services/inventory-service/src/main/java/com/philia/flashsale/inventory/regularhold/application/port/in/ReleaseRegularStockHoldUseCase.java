package com.philia.flashsale.inventory.regularhold.application.port.in;

import com.philia.flashsale.inventory.regularhold.application.command.ReleaseRegularStockHoldCommand;
import com.philia.flashsale.inventory.regularhold.application.result.RegularStockHoldReleaseResult;

public interface ReleaseRegularStockHoldUseCase {
    RegularStockHoldReleaseResult release(ReleaseRegularStockHoldCommand command);
}
