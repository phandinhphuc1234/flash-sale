package com.philia.flashsale.product.catalog.adapter.out.persistence;

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
                    WITH RECURSIVE category_tree AS (
                        SELECT c.id
                        FROM categories c
                        WHERE :categorySlug IS NOT NULL
                          AND c.slug = :categorySlug
                          AND c.status = 'ACTIVE'
                        UNION ALL
                        SELECT child.id
                        FROM categories child
                        JOIN category_tree parent ON parent.id = child.parent_id
                        WHERE child.status = 'ACTIVE'
                    )
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
                      AND (:query IS NULL OR (
                          LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
                          OR LOWER(p.code) LIKE LOWER(CONCAT('%', :query, '%'))
                          OR LOWER(COALESCE(p.slug, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                          OR LOWER(COALESCE(p.short_description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                          OR EXISTS (
                              SELECT 1
                              FROM product_variants search_variant
                              WHERE search_variant.product_id = p.id
                                AND search_variant.status = 'ACTIVE'
                                AND (
                                    LOWER(search_variant.name) LIKE LOWER(CONCAT('%', :query, '%'))
                                    OR LOWER(search_variant.sku) LIKE LOWER(CONCAT('%', :query, '%'))
                                )
                          )
                      ))
                      AND (:categorySlug IS NULL OR EXISTS (
                          SELECT 1
                          FROM product_categories pc
                          JOIN category_tree ct ON ct.id = pc.category_id
                          WHERE pc.product_id = p.id
                      ))
                      AND (:minPrice IS NULL OR EXISTS (
                          SELECT 1
                          FROM product_variants price_variant
                          WHERE price_variant.product_id = p.id
                            AND price_variant.status = 'ACTIVE'
                            AND price_variant.base_price >= :minPrice
                      ))
                      AND (:maxPrice IS NULL OR EXISTS (
                          SELECT 1
                          FROM product_variants price_variant
                          WHERE price_variant.product_id = p.id
                            AND price_variant.status = 'ACTIVE'
                            AND price_variant.base_price <= :maxPrice
                      ))
                    ORDER BY
                        CASE WHEN :sort = 'RELEVANCE' AND :query IS NOT NULL THEN
                            CASE
                                WHEN LOWER(p.name) = LOWER(:query) THEN 0
                                WHEN LOWER(p.name) LIKE LOWER(CONCAT(:query, '%')) THEN 1
                                ELSE 2
                            END
                        ELSE 0 END ASC,
                        CASE WHEN :sort = 'PRICE_ASC' THEN (
                            SELECT MIN(price_variant.base_price)
                            FROM product_variants price_variant
                            WHERE price_variant.product_id = p.id
                              AND price_variant.status = 'ACTIVE'
                        ) END ASC,
                        CASE WHEN :sort = 'PRICE_DESC' THEN (
                            SELECT MIN(price_variant.base_price)
                            FROM product_variants price_variant
                            WHERE price_variant.product_id = p.id
                              AND price_variant.status = 'ACTIVE'
                        ) END DESC,
                        CASE WHEN :sort IN ('NEWEST', 'RELEVANCE') THEN p.published_at END DESC,
                        p.id ASC
                    """,
            countQuery = """
                    WITH RECURSIVE category_tree AS (
                        SELECT c.id
                        FROM categories c
                        WHERE :categorySlug IS NOT NULL
                          AND c.slug = :categorySlug
                          AND c.status = 'ACTIVE'
                        UNION ALL
                        SELECT child.id
                        FROM categories child
                        JOIN category_tree parent ON parent.id = child.parent_id
                        WHERE child.status = 'ACTIVE'
                    )
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
                      AND (:query IS NULL OR (
                          LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
                          OR LOWER(p.code) LIKE LOWER(CONCAT('%', :query, '%'))
                          OR LOWER(COALESCE(p.slug, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                          OR LOWER(COALESCE(p.short_description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                          OR EXISTS (
                              SELECT 1
                              FROM product_variants search_variant
                              WHERE search_variant.product_id = p.id
                                AND search_variant.status = 'ACTIVE'
                                AND (
                                    LOWER(search_variant.name) LIKE LOWER(CONCAT('%', :query, '%'))
                                    OR LOWER(search_variant.sku) LIKE LOWER(CONCAT('%', :query, '%'))
                                )
                          )
                      ))
                      AND (:categorySlug IS NULL OR EXISTS (
                          SELECT 1
                          FROM product_categories pc
                          JOIN category_tree ct ON ct.id = pc.category_id
                          WHERE pc.product_id = p.id
                      ))
                      AND (:minPrice IS NULL OR EXISTS (
                          SELECT 1
                          FROM product_variants price_variant
                          WHERE price_variant.product_id = p.id
                            AND price_variant.status = 'ACTIVE'
                            AND price_variant.base_price >= :minPrice
                      ))
                      AND (:maxPrice IS NULL OR EXISTS (
                          SELECT 1
                          FROM product_variants price_variant
                          WHERE price_variant.product_id = p.id
                            AND price_variant.status = 'ACTIVE'
                            AND price_variant.base_price <= :maxPrice
                      ))
                    """,
            nativeQuery = true)
    Page<ProductRow> findVisibleProducts(
            @Param("query") String query,
            @Param("categorySlug") String categorySlug,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("sort") String sort,
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

    @Query(value = """
            SELECT v.id AS variantId,
                   p.id AS productId,
                   p.slug AS productSlug,
                   p.name AS productName,
                   v.name AS variantName,
                   v.sku AS sku,
                   v.base_price AS basePrice,
                   v.currency AS currency,
                   CASE WHEN p.status = 'ACTIVE'
                              AND p.published_at IS NOT NULL
                              AND p.published_at <= CURRENT_TIMESTAMP
                              AND v.status = 'ACTIVE'
                              AND v.base_price IS NOT NULL
                              AND v.base_price > 0
                              AND v.currency = 'VND'
                        THEN true ELSE false END AS sellable,
                   COALESCE(
                       (SELECT m.url
                          FROM product_media m
                         WHERE m.variant_id = v.id
                           AND m.status = 'ACTIVE'
                         ORDER BY m.sort_order, m.id
                         LIMIT 1),
                       (SELECT m.url
                          FROM product_media m
                         WHERE m.product_id = p.id
                           AND m.variant_id IS NULL
                           AND m.status = 'ACTIVE'
                         ORDER BY m.sort_order, m.id
                         LIMIT 1)) AS primaryImageUrl
            FROM product_variants v
            JOIN products p ON p.id = v.product_id
            WHERE v.id IN (:variantIds)
            """, nativeQuery = true)
    List<VariantDisplayRow> findVariantDisplaysByIds(@Param("variantIds") Collection<UUID> variantIds);

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

    interface VariantDisplayRow {
        UUID getVariantId();

        UUID getProductId();

        String getProductSlug();

        String getProductName();

        String getVariantName();

        String getSku();

        BigDecimal getBasePrice();

        String getCurrency();

        boolean isSellable();

        String getPrimaryImageUrl();
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
