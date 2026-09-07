package com.philia.flashsale.order.regularpurchase.application.model;

import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequest;
import java.time.Instant;
import java.util.Objects;

/** One stale intake claimed by a recovery worker for a bounded period. */
public record RegularPurchaseRecoveryClaim(RegularPurchaseRequest request, String workerId,
        Instant leaseUntil) {
    public RegularPurchaseRecoveryClaim {
        Objects.requireNonNull(request, "request");
        if (workerId == null || workerId.isBlank() || workerId.length() > 128) {
            throw new IllegalArgumentException("workerId must be one to 128 characters");
        }
        Objects.requireNonNull(leaseUntil, "leaseUntil");
    }
}
