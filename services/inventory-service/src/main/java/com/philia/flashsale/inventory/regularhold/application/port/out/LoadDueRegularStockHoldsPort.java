package com.philia.flashsale.inventory.regularhold.application.port.out;

import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHold;
import java.time.Instant;
import java.util.List;

public interface LoadDueRegularStockHoldsPort {
    List<RegularStockHold> findDueForUpdate(Instant now, int limit);
}
