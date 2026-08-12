package com.philia.flashsale.product.catalogadmin.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProductAdminJpaRepository extends JpaRepository<AdminProductJpaEntity, UUID> {

    String SEARCH_ADMIN_PRODUCTS_SQL = """
            SELECT p.*
            FROM products p
            WHERE (:status IS NULL OR p.status = :status)
              AND (
                :q IS NULL
                OR lower(p.code) LIKE concat('%', lower(cast(:q AS text)), '%') ESCAPE '\\'
                OR lower(p.slug) LIKE concat('%', lower(cast(:q AS text)), '%') ESCAPE '\\'
                OR lower(p.name) LIKE concat('%', lower(cast(:q AS text)), '%') ESCAPE '\\'
                OR EXISTS (
                    SELECT 1
                    FROM product_variants v
                    WHERE v.product_id = p.id
                      AND lower(v.sku) LIKE concat('%', lower(cast(:q AS text)), '%') ESCAPE '\\'
                )
              )
            ORDER BY p.updated_at DESC, p.id ASC
            """;

    String COUNT_ADMIN_PRODUCTS_SQL = """
            SELECT count(*)
            FROM products p
            WHERE (:status IS NULL OR p.status = :status)
              AND (
                :q IS NULL
                OR lower(p.code) LIKE concat('%', lower(cast(:q AS text)), '%') ESCAPE '\\'
                OR lower(p.slug) LIKE concat('%', lower(cast(:q AS text)), '%') ESCAPE '\\'
                OR lower(p.name) LIKE concat('%', lower(cast(:q AS text)), '%') ESCAPE '\\'
                OR EXISTS (
                    SELECT 1
                    FROM product_variants v
                    WHERE v.product_id = p.id
                      AND lower(v.sku) LIKE concat('%', lower(cast(:q AS text)), '%') ESCAPE '\\'
                )
              )
            """;

    boolean existsByCode(String code);

    boolean existsBySlug(String slug);

    @Query(value = SEARCH_ADMIN_PRODUCTS_SQL, countQuery = COUNT_ADMIN_PRODUCTS_SQL,
            nativeQuery = true)
    Page<AdminProductJpaEntity> searchAdminProducts(
            @Param("status") String status,
            @Param("q") String q,
            Pageable pageable);
}

interface ProductAdminVariantJpaRepository
        extends JpaRepository<AdminProductVariantJpaEntity, UUID> {

    boolean existsBySku(String sku);

    boolean existsByBarcode(String barcode);

    List<AdminProductVariantJpaEntity> findByProductIdOrderBySortOrderAscIdAsc(UUID productId);

    void deleteByProductId(UUID productId);
}

interface ProductAdminCategoryJpaRepository
        extends JpaRepository<AdminProductCategoryJpaEntity, AdminProductCategoryId> {

    List<AdminProductCategoryJpaEntity> findByProductIdOrderBySortOrderAscCategoryIdAsc(UUID productId);

    void deleteByProductId(UUID productId);

    @Query("select c.id from CategoryJpaEntity c where c.id in :ids")
    List<UUID> findExistingCategoryIds(@Param("ids") Collection<UUID> ids);
}

interface ProductAdminMediaJpaRepository
        extends JpaRepository<AdminProductMediaJpaEntity, UUID> {

    List<AdminProductMediaJpaEntity> findByProductIdOrderBySortOrderAscIdAsc(UUID productId);

    void deleteByProductId(UUID productId);
}

interface ProductAdminIdempotencyJpaRepository
        extends JpaRepository<AdminIdempotencyJpaEntity, UUID> {

    Optional<AdminIdempotencyJpaEntity> findByActorIdAndIdempotencyKey(
            String actorId,
            String idempotencyKey);
}

interface ProductAdminAuditJpaRepository
        extends JpaRepository<AdminAuditJpaEntity, UUID> {
}
