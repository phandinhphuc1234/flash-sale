package com.philia.flashsale.product.adapter.out.persistence.admin;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity(name = "AdminProductCategoryJpaEntity")
@Table(name = "product_categories")
@IdClass(AdminProductCategoryId.class)
class AdminProductCategoryJpaEntity {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Id
    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "is_primary")
    private boolean primary;

    @Column(name = "sort_order")
    private int sortOrder;

    protected AdminProductCategoryJpaEntity() {
    }

    UUID categoryId() {
        return categoryId;
    }

    boolean primary() {
        return primary;
    }

    int sortOrder() {
        return sortOrder;
    }
}
