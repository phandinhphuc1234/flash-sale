package com.philia.flashsale.product.catalogadmin.domain;

import java.time.Instant;
import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.domain.exception.ArchivedProductImmutableException;

public final class ProductAggregate {

    private final UUID id;
    private final String code;
    private final String slug;
    private final String name;
    private final String shortDescription;
    private final String description;
    private final ProductStatus status;
    private final Instant publishedAt;
    private final long version;

    private ProductAggregate(
            UUID id,
            String code,
            String slug,
            String name,
            String shortDescription,
            String description,
            ProductStatus status,
            Instant publishedAt,
            long version) {
        this.id = id;
        this.code = requireText(code, "Product code is required");
        this.slug = requireText(slug, "Product slug is required");
        this.name = requireText(name, "Product name is required");
        this.shortDescription = normalizeOptional(shortDescription);
        this.description = normalizeOptional(description);
        this.status = status == null ? ProductStatus.DRAFT : status;
        this.publishedAt = publishedAt;
        this.version = version;
    }

    // Factory method keeps the initial draft state inside the domain instead of letting controllers choose it.
    public static ProductAggregate createDraft(
            UUID id,
            String code,
            String slug,
            String name,
            String shortDescription,
            String description) {
        UUID productId = id == null ? UUID.randomUUID() : id;
        return new ProductAggregate(
                productId,
                code,
                slug,
                name,
                shortDescription,
                description,
                ProductStatus.DRAFT,
                null,
                0);
    }

    // Rehydration rebuilds a domain object from persistence without re-running a creation workflow.
    public static ProductAggregate rehydrate(
            UUID id,
            String code,
            String slug,
            String name,
            String shortDescription,
            String description,
            ProductStatus status,
            Instant publishedAt,
            long version) {
        return new ProductAggregate(
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

    // Archived products are terminal; application services call this before future mutation use cases.
    public void ensureMutable() {
        if (status.isArchived()) {
            throw new ArchivedProductImmutableException("Archived Product cannot be mutated");
        }
    }

    // Public catalog visibility is stricter than admin visibility and also requires variants outside this aggregate.
    public boolean hiddenFromShopperCatalogWithoutVariants() {
        return status != ProductStatus.ACTIVE || publishedAt == null;
    }

    public UUID id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String slug() {
        return slug;
    }

    public String name() {
        return name;
    }

    public String shortDescription() {
        return shortDescription;
    }

    public String description() {
        return description;
    }

    public ProductStatus status() {
        return status;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public long version() {
        return version;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
