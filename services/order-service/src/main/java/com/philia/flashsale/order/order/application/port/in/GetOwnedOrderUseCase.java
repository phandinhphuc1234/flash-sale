package com.philia.flashsale.order.order.application.port.in;

import com.philia.flashsale.order.order.application.query.GetOwnedOrderQuery;
import com.philia.flashsale.order.order.application.result.OrderDetailsResult;

/** Inbound capability for an owner-scoped Order detail query. */
public interface GetOwnedOrderUseCase {
    OrderDetailsResult get(GetOwnedOrderQuery query);
}
