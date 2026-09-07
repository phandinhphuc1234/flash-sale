package com.philia.flashsale.product.catalogquery.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Product-only database projection for checkout decisions; it intentionally has no reservation state. */
interface PurchaseQuoteJpaRepository extends JpaRepository<PurchaseQuoteJpaEntity, UUID> {

    @Query(value = """
            SELECT v.id AS variantId,
                   p.id AS productId,
                   v.sku AS sku,
                   p.name AS productName,
                   v.name AS variantName,
                   v.base_price AS unitPrice,
                   v.currency AS currency,
                   v.version AS catalogVersion,
                   CASE WHEN p.status = 'ACTIVE'
                              AND p.published_at IS NOT NULL
                              AND p.published_at <= CURRENT_TIMESTAMP
                              AND v.status = 'ACTIVE'
                              AND v.base_price > 0
                              AND v.currency = 'VND'
                        THEN true ELSE false END AS sellable,
                   CASE WHEN p.status = 'ACTIVE'
                              AND p.published_at IS NOT NULL
                              AND p.published_at <= CURRENT_TIMESTAMP
                              AND v.status = 'ACTIVE'
                              AND v.base_price > 0
                              AND v.currency = 'VND'
                        THEN NULL ELSE 'NOT_SELLABLE' END AS unavailableReason
            FROM product_variants v
            JOIN products p ON p.id = v.product_id
            WHERE v.id IN (:variantIds)
            """, nativeQuery = true)
    List<PurchaseQuoteRow> findPurchaseQuotesByVariantIds(@Param("variantIds") Collection<UUID> variantIds);

    interface PurchaseQuoteRow {
        UUID getVariantId();

        UUID getProductId();

        String getSku();

        String getProductName();

        String getVariantName();

        BigDecimal getUnitPrice();

        String getCurrency();

        long getCatalogVersion();

        boolean isSellable();

        String getUnavailableReason();
    }
}
