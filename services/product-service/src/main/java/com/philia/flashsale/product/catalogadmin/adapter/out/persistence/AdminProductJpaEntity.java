package com.philia.flashsale.product.catalogadmin.adapter.out.persistence;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.domain.ProductAggregate;
import com.philia.flashsale.product.catalogadmin.domain.ProductStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "AdminProductJpaEntity")
@Table(name = "products")
class AdminProductJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(name = "short_description")
    private String shortDescription;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> attributes = Map.of();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AdminProductJpaEntity() {
    }

    private AdminProductJpaEntity(ProductAggregate product) {
        this.id = product.id();
        this.code = product.code();
        this.slug = product.slug();
        this.name = product.name();
        this.shortDescription = product.shortDescription();
        this.description = product.description();
        this.status = product.status();
        this.publishedAt = product.publishedAt();
        this.version = product.version();
    }

    static AdminProductJpaEntity from(ProductAggregate product) {
        return new AdminProductJpaEntity(product);
    }

    void updateContent(String name, String shortDescription, String description) {
        this.name = name;
        this.shortDescription = shortDescription;
        this.description = description;
    }

    void updateLifecycle(ProductStatus status, Instant publishedAt) {
        this.status = status;
        this.publishedAt = publishedAt;
    }

    ProductAggregate toAggregate() {
        return ProductAggregate.rehydrate(
                id,
                code,
                slug,
                name,
                shortDescription,
                description,
                status,
                publishedAt,
                version);
    }

    UUID id() {
        return id;
    }

    String code() {
        return code;
    }

    String slug() {
        return slug;
    }

    String name() {
        return name;
    }

    String shortDescription() {
        return shortDescription;
    }

    String description() {
        return description;
    }

    ProductStatus status() {
        return status;
    }

    Instant publishedAt() {
        return publishedAt;
    }

    long version() {
        return version;
    }
}
