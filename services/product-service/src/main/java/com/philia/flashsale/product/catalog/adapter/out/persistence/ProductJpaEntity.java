package com.philia.flashsale.product.catalog.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
class ProductJpaEntity {

    @Id
    private UUID id;

    private String code;

    private String slug;

    private String name;

    @Column(name = "short_description")
    private String shortDescription;

    private String description;

    private String status;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected ProductJpaEntity() {
    }

    UUID getId() {
        return id;
    }
}
