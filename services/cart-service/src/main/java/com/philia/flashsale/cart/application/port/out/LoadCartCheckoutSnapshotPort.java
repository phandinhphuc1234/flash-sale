package com.philia.flashsale.cart.application.port.out;

import com.philia.flashsale.cart.application.result.CartCheckoutSnapshotResult;
import java.util.Optional;
import java.util.UUID;

/** Cart-owned snapshot read; Product enrichment is intentionally outside this capability. */
public interface LoadCartCheckoutSnapshotPort {
    Optional<CartCheckoutSnapshotResult> loadCheckoutSnapshot(UUID ownerId);
}
