package com.philia.flashsale.product.catalogadmin.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

class AdminProductCategoryId implements Serializable {

    private UUID productId;
    private UUID categoryId;

    AdminProductCategoryId() {
    }

    AdminProductCategoryId(UUID productId, UUID categoryId) {
        this.productId = productId;
        this.categoryId = categoryId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AdminProductCategoryId that)) {
            return false;
        }
        return Objects.equals(productId, that.productId)
                && Objects.equals(categoryId, that.categoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, categoryId);
    }
}
