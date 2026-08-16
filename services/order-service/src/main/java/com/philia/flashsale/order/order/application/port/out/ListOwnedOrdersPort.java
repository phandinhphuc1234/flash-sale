package com.philia.flashsale.order.order.application.port.out;

import com.philia.flashsale.order.order.application.query.ListOwnedOrdersQuery;
import com.philia.flashsale.order.order.application.result.OrderPageResult;

/** Owner-scoped page lookup capability required by the application core. */
public interface ListOwnedOrdersPort {
    OrderPageResult load(ListOwnedOrdersQuery query);
}
