package com.philia.flashsale.product.catalog.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
class CategoryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    private String slug;

    private String name;

    private String status;

    @Column(name = "sort_order")
    private int sortOrder;

    protected CategoryJpaEntity() {
    }

    UUID getId() {
        return id;
    }

    UUID getParentId() {
        return parentId;
    }

    String getSlug() {
        return slug;
    }

    String getName() {
        return name;
    }

    int getSortOrder() {
        return sortOrder;
    }
}
