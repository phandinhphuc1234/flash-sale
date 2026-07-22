package com.philia.flashsale.product.domain.model;

public enum ProductStatus {
    DRAFT,
    ACTIVE,
    INACTIVE,
    ARCHIVED;

    public boolean isShopperVisibleBaseState() {
        return this == ACTIVE;
    }

    public boolean isArchived() {
        return this == ARCHIVED;
    }
}
