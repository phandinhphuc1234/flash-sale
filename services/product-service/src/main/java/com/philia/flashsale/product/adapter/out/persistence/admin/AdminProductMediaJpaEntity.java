package com.philia.flashsale.product.adapter.out.persistence.admin;

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
