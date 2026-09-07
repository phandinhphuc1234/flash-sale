package com.philia.flashsale.order.regularpurchase.application.port.out;

import com.philia.flashsale.order.regularpurchase.application.model.RegularPurchaseAcceptance;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequest;
import java.util.Optional;
import java.util.UUID;

/** Order-owned durability capability for resumable regular-purchase intake checkpoints. */
public interface PersistRegularPurchasePort {

    Optional<RegularPurchaseRequest> findByShopperAndIdempotencyKey(UUID shopperId, String idempotencyKey);

    /** Creates a new idempotency intake or returns the existing scoped intake unchanged. */
    RegularPurchaseRequest register(RegularPurchaseRequest request);

    /** Persists one non-terminal checkpoint after its external owner returned a decision. */
    RegularPurchaseRequest update(RegularPurchaseRequest request);

    /** Atomically commits accepted intake, Order/lines, Saga, and the two required outbox intents. */
    RegularPurchaseRequest accept(RegularPurchaseAcceptance acceptance);
}
