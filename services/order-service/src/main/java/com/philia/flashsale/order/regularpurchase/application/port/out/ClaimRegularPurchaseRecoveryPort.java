package com.philia.flashsale.order.regularpurchase.application.port.out;

import com.philia.flashsale.order.regularpurchase.application.model.RegularPurchaseRecoveryClaim;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Claims stale non-terminal intake rows without holding a database lock during HTTP calls. */
public interface ClaimRegularPurchaseRecoveryPort {
    List<RegularPurchaseRecoveryClaim> claim(String workerId, Instant now, int batchSize, Duration lease);

    void release(UUID requestId, String workerId);
}
