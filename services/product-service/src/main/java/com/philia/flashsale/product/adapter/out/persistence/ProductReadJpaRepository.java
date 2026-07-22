package com.philia.flashsale.product.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProductReadJpaRepository extends JpaRepository<ProductJpaEntity, UUID> {

    @Query(
            value = """
                    SELECT p.id AS id,
                           p.code AS code,
                           p.slug AS slug,
                           p.name AS name,
                           p.short_description AS shortDescription,
                           p.description AS description
                    FROM products p
                    WHERE p.status = 'ACTIVE'
                      AND p.published_at IS NOT NULL
                      AND p.published_at <= CURRENT_TIMESTAMP
                      AND EXISTS (
                          SELECT 1
                          FROM product_variants v
                          WHERE v.product_id = p.id
                            AND v.status = 'ACTIVE'
                      )
                    ORDER BY p.published_at DESC, p.id DESC
                    """,
            countQuery = """
                    SELECT count(*)
                    FROM products p
                    WHERE p.status = 'ACTIVE'
                      AND p.published_at IS NOT NULL
                      AND p.published_at <= CURRENT_TIMESTAMP
                      AND EXISTS (
                          SELECT 1
                          FROM product_variants v
                          WHERE v.product_id = p.id
                            AND v.status = 'ACTIVE'
                      )
                    """,
            nativeQuery = true)
    Page<ProductRow> findVisibleProducts(Pageable pageable);

    @Query(
            value = """
                    SELECT p.id AS id,
                           p.code AS code,
                           p.slug AS slug,
                           p.name AS name,
                           p.short_description AS shortDescription,
                           p.description AS description
                    FROM products p
                    JOIN product_categories pc
                      ON pc.product_id = p.id
                    JOIN categories c
                      ON c.id = pc.category_id
                    WHERE c.slug = :categorySlug
                      AND p.status = 'ACTIVE'
                      AND p.published_at IS NOT NULL
                      AND p.published_at <= CURRENT_TIMESTAMP
                      AND EXISTS (
                          SELECT 1
                          FROM product_variants v
                          WHERE v.product_id = p.id
                            AND v.status = 'ACTIVE'
                      )
                    ORDER BY p.published_at DESC, p.id DESC
                    """,
            countQuery = """
                    SELECT count(*)
                    FROM products p
                    JOIN product_categories pc
                      ON pc.product_id = p.id
                    JOIN categories c
                      ON c.id = pc.category_id
                    WHERE c.slug = :categorySlug
                      AND p.status = 'ACTIVE'
                      AND p.published_at IS NOT NULL
                      AND p.published_at <= CURRENT_TIMESTAMP
                      AND EXISTS (
                          SELECT 1
                          FROM product_variants v
                          WHERE v.product_id = p.id
                            AND v.status = 'ACTIVE'
                      )
                    """,
            nativeQuery = true)
    Page<ProductRow> findVisibleProductsByCategorySlug(
            @Param("categorySlug") String categorySlug,
            Pageable pageable);

    @Query(
            value = """
                    SELECT p.id AS id,
                           p.code AS code,
                           p.slug AS slug,
                           p.name AS name,
                           p.short_description AS shortDescription,
                           p.description AS description
                    FROM products p
                    WHERE p.slug = :slug
                      AND p.status = 'ACTIVE'
                      AND p.published_at IS NOT NULL
                      AND p.published_at <= CURRENT_TIMESTAMP
                      AND EXISTS (
                          SELECT 1
                          FROM product_variants v
                          WHERE v.product_id = p.id
                            AND v.status = 'ACTIVE'
                      )
                    """,
            nativeQuery = true)
    Optional<ProductRow> findVisibleProductBySlug(@Param("slug") String slug);

    @Query(
            value = """
                    SELECT v.product_id AS productId,
                           v.id AS id,
                           v.sku AS sku,
                           v.name AS name,
                           v.base_price AS basePrice,
                           v.currency AS currency
                    FROM product_variants v
                    WHERE v.product_id IN (:productIds)
                      AND v.status = 'ACTIVE'
                    ORDER BY v.product_id, v.sort_order, v.id
                    """,
            nativeQuery = true)
    List<VariantRow> findActiveVariantsByProductIds(@Param("productIds") Collection<UUID> productIds);

    @Query(
            value = """
                    SELECT pc.product_id AS productId,
                           c.id AS id,
                           c.slug AS slug,
                           c.name AS name,
                           pc.is_primary AS primaryCategory,
                           pc.sort_order AS sortOrder
                    FROM product_categories pc
                    JOIN categories c
                      ON c.id = pc.category_id
                    WHERE pc.product_id IN (:productIds)
                    ORDER BY pc.product_id, pc.is_primary DESC, pc.sort_order, c.id
                    """,
            nativeQuery = true)
    List<ProductCategoryRow> findCategoriesByProductIds(@Param("productIds") Collection<UUID> productIds);

    @Query(
            value = """
                    SELECT m.product_id AS productId,
                           m.id AS id,
                           m.media_type AS mediaType,
                           m.url AS url,
                           m.alt_text AS altText,
                           m.sort_order AS sortOrder
                    FROM product_media m
                    WHERE m.product_id IN (:productIds)
                      AND m.status = 'ACTIVE'
                    ORDER BY m.product_id, m.sort_order, m.id
                    """,
            nativeQuery = true)
    List<ProductMediaRow> findActiveMediaByProductIds(@Param("productIds") Collection<UUID> productIds);

    interface ProductRow {
        UUID getId();

        String getCode();

        String getSlug();

        String getName();

        String getShortDescription();

        String getDescription();
    }

    interface VariantRow {
        UUID getProductId();

        UUID getId();

        String getSku();

        String getName();

        BigDecimal getBasePrice();

        String getCurrency();
    }

    interface ProductCategoryRow {
        UUID getProductId();

        UUID getId();

        String getSlug();

        String getName();

        boolean isPrimaryCategory();

        int getSortOrder();
    }

    interface ProductMediaRow {
        UUID getProductId();

        UUID getId();

        String getMediaType();

        String getUrl();

        String getAltText();

        int getSortOrder();
    }
}
