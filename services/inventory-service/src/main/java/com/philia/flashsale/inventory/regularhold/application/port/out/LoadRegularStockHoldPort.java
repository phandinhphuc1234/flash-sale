package com.philia.flashsale.inventory.regularhold.application.port.out;

import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHold;
import java.util.Optional;
import java.util.UUID;

/** Loads stable regular-hold identities for idempotent create handling. */
public interface LoadRegularStockHoldPort {
    Optional<RegularStockHold> findByPurchaseRequestId(UUID purchaseRequestId);
    Optional<RegularStockHold> findById(UUID holdId);
    Optional<RegularStockHold> findByIdForUpdate(UUID holdId);
    Optional<RegularStockHold> findByOrderId(UUID orderId);
}
