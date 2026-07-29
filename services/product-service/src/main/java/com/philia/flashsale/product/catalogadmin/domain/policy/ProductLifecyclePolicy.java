package com.philia.flashsale.product.catalogadmin.domain.policy;

import com.philia.flashsale.product.catalogadmin.domain.exception.InvalidLifecycleTransitionException;
import com.philia.flashsale.product.catalogadmin.domain.ProductStatus;

public final class ProductLifecyclePolicy {

    // Centralize lifecycle transitions so adapters cannot set arbitrary product statuses.
    public void requireAllowedTransition(ProductStatus current, ProductStatus target) {
        if (current == ProductStatus.ARCHIVED) {
            throw new InvalidLifecycleTransitionException("Archived Product is final");
        }
        if (!isAllowed(current, target)) {
            throw new InvalidLifecycleTransitionException(
                    "Transition from " + current + " to " + target + " is not allowed");
        }
    }

    // Only business-approved transitions belong here; API shape and database state do not decide lifecycle rules.
    private boolean isAllowed(ProductStatus current, ProductStatus target) {
        return (current == ProductStatus.DRAFT && target == ProductStatus.ACTIVE)
                || (current == ProductStatus.ACTIVE && target == ProductStatus.INACTIVE)
                || (current == ProductStatus.INACTIVE && target == ProductStatus.ACTIVE)
                || (target == ProductStatus.ARCHIVED && current != ProductStatus.ARCHIVED);
    }
}
