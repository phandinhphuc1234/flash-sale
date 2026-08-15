package com.philia.flashsale.order.order.application.port.out;

import com.philia.flashsale.order.order.application.model.OrderCreationCandidate;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;

/** Atomic capability for Order, line, inbox, and creation-outbox durability. */
public interface PersistOrderCreationPort {
    OrderCreationResult persist(OrderCreationCandidate candidate);
}
