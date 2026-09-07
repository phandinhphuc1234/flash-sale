package com.philia.flashsale.order.regularpurchase.application.port.out;

import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHold;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHoldCommand;

/** Creates or replays Inventory's idempotent regular-stock hold using durable Order identities. */
public interface CreateRegularStockHoldPort {
    RegularStockHold create(RegularStockHoldCommand command, String traceId);
}
