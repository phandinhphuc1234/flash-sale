package com.philia.flashsale.order.regularpurchase.application.exception;

/** Safe rollout/rollback boundary while the regular-purchase intake feature flag is disabled. */
public final class RegularPurchaseDisabledException extends RuntimeException {
    public RegularPurchaseDisabledException() {
        super("Regular purchase intake is disabled");
    }
}
