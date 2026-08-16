package com.philia.flashsale.order.order.application.port.out;

import com.philia.flashsale.order.order.application.query.GetOwnedOrderQuery;
import com.philia.flashsale.order.order.application.result.OrderDetailsResult;
import java.util.Optional;

/** Owner-scoped detail lookup capability required by the application core. */
public interface LoadOwnedOrderPort {
    Optional<OrderDetailsResult> load(GetOwnedOrderQuery query);
}
