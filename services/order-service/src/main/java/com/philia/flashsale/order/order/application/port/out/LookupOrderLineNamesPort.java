package com.philia.flashsale.order.order.application.port.out;

import com.philia.flashsale.order.order.application.model.OrderLineNames;
import java.util.Optional;
import java.util.UUID;

/** Best-effort Product metadata lookup used only while creating a Flash Sale Order. */
@FunctionalInterface
public interface LookupOrderLineNamesPort {
    Optional<OrderLineNames> lookup(UUID variantId, String traceId);
}
