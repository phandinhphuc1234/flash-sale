package com.philia.flashsale.product.campaignvalidation.adapter.out.persistence;

import com.philia.flashsale.product.campaignvalidation.application.port.out.LoadCampaignVariantPort;
import com.philia.flashsale.product.campaignvalidation.domain.CampaignVariantSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Reads a Product-owned projection without exposing Product JPA entities to the use case. */
@Repository
@Transactional(readOnly = true)
public class ProductCampaignValidationPersistenceAdapter implements LoadCampaignVariantPort {
    private final JdbcTemplate jdbc;
    public ProductCampaignValidationPersistenceAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<CampaignVariantSnapshot> load(UUID variantId) {
        return jdbc.query("""
                SELECT p.id AS product_id,
                       v.id AS variant_id,
                       v.sku,
                       p.status AS product_status,
                       p.published_at,
                       v.status AS variant_status,
                       v.base_price,
                       v.currency
                FROM product_variants v
                JOIN products p ON p.id = v.product_id
                WHERE v.id = ?
                """, rs -> rs.next()
                ? Optional.of(new CampaignVariantSnapshot(
                        rs.getObject("product_id", UUID.class),
                        rs.getObject("variant_id", UUID.class),
                        rs.getString("sku"),
                        rs.getString("product_status"),
                        rs.getObject("published_at", java.time.OffsetDateTime.class),
                        rs.getString("variant_status"),
                        rs.getBigDecimal("base_price"),
                        rs.getString("currency")))
                : Optional.empty(), variantId);
    }
}
