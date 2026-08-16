package com.philia.flashsale.order.order.application.port.in;

import com.philia.flashsale.order.order.application.query.ListOwnedOrdersQuery;
import com.philia.flashsale.order.order.application.result.OrderPageResult;

/** Inbound capability for a bounded owner-scoped Order list query. */
public interface ListOwnedOrdersUseCase {
    OrderPageResult list(ListOwnedOrdersQuery query);
}
