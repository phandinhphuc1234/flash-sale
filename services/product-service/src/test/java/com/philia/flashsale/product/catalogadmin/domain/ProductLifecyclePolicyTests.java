package com.philia.flashsale.product.catalogadmin.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.philia.flashsale.product.catalogadmin.domain.exception.InvalidLifecycleTransitionException;
import com.philia.flashsale.product.catalogadmin.domain.policy.ProductLifecyclePolicy;

class ProductLifecyclePolicyTests {

    private final ProductLifecyclePolicy policy = new ProductLifecyclePolicy();

    @Test
    void allowsApprovedLifecycleTransitions() {
        policy.requireAllowedTransition(ProductStatus.DRAFT, ProductStatus.ACTIVE);
        policy.requireAllowedTransition(ProductStatus.ACTIVE, ProductStatus.INACTIVE);
        policy.requireAllowedTransition(ProductStatus.INACTIVE, ProductStatus.ACTIVE);
        policy.requireAllowedTransition(ProductStatus.ACTIVE, ProductStatus.ARCHIVED);
    }

    @Test
    void archivedProductIsFinal() {
        assertThatThrownBy(() -> policy.requireAllowedTransition(ProductStatus.ARCHIVED, ProductStatus.ACTIVE))
                .isInstanceOf(InvalidLifecycleTransitionException.class);
    }
}
