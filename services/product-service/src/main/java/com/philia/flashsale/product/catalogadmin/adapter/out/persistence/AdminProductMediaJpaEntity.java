package com.philia.flashsale.product.catalogadmin.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "AdminProductMediaJpaEntity")
@Table(name = "product_media")
class AdminProductMediaJpaEntity {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(name = "media_type", nullable = false, length = 20)
    private String mediaType;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "alt_text", length = 500)
    private String altText;

    @Column(name = "sort_order")
    private int sortOrder;

    @Column(nullable = false, length = 20)
    private String status;

    protected AdminProductMediaJpaEntity() {
    }

    static AdminProductMediaJpaEntity from(UUID productId,
            com.philia.flashsale.product.catalogadmin.application.command.MaintainProductCompositionCommand.MediaInput input) {
        AdminProductMediaJpaEntity entity = new AdminProductMediaJpaEntity();
        entity.id = input.id() == null ? UUID.randomUUID() : input.id();
        entity.productId = productId;
        entity.variantId = input.variantId();
        entity.mediaType = input.mediaType();
        entity.url = input.url().trim();
        entity.altText = input.altText();
        entity.sortOrder = input.sortOrder();
        entity.status = input.status() == null ? "ACTIVE" : input.status();
        return entity;
    }

    UUID id() {
        return id;
    }

    UUID variantId() {
        return variantId;
    }

    String mediaType() {
        return mediaType;
    }

    String url() {
        return url;
    }

    String altText() {
        return altText;
    }

    int sortOrder() {
        return sortOrder;
    }

    String status() {
        return status;
    }
}
