package com.philia.flashsale.inventory.regularhold.application.port.out;

import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHold;

/** Persists a regular hold and every canonical item in the caller's local transaction. */
public interface SaveRegularStockHoldPort {
    RegularStockHold save(RegularStockHold hold);
}
