package com.philia.flashsale.product.campaignvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Executable baseline for Product's internal campaign-validation boundary.
 *
 * <p>The database projection test is green because Product already owns the source tables. The
 * endpoint and internal-token assertions intentionally become red until T048-T050 add the
 * campaign-validation feature and its dedicated security chain.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProductCampaignValidationTests {

    private static final String INTERNAL_AUDIENCE = "flash-sale-internal-api";
    private static final String PUBLIC_AUDIENCE = "flash-sale-api";
    private static final String CAMPAIGN_SUBJECT = "campaign-service";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("product_db")
            .withUsername("product")
            .withPassword("product");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @BeforeEach
    void clearBusinessData() {
        jdbc.execute("""
                TRUNCATE TABLE
                    product_admin_audit_logs,
                    product_admin_idempotency_keys,
                    product_media,
                    product_categories,
                    product_variants,
                    products,
                    categories
                """);
    }

    @Test
    void persistenceProjectionReturnsOnlyActivePublishedProductWithActiveVariant() {
        UUID sellableProduct = insertProduct("PROD-SELLABLE", "sellable", "ACTIVE", true);
        UUID sellableVariant = insertVariant(sellableProduct, "SKU-SELLABLE", "ACTIVE", "1000.0000");
        UUID unpublishedProduct = insertProduct("PROD-UNPUBLISHED", "unpublished", "ACTIVE", false);
        insertVariant(unpublishedProduct, "SKU-UNPUBLISHED", "ACTIVE", "1000.0000");
        UUID inactiveVariantProduct = insertProduct("PROD-INACTIVE-VARIANT", "inactive-variant", "ACTIVE", true);
        insertVariant(inactiveVariantProduct, "SKU-INACTIVE", "INACTIVE", "1000.0000");

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.id AS product_id,
                       v.id AS variant_id,
                       v.sku,
                       p.status AS product_status,
                       v.status AS variant_status,
                       v.base_price,
                       v.currency
                FROM products p
                JOIN product_variants v ON v.product_id = p.id
                WHERE v.id = ?
                  AND p.status = 'ACTIVE'
                  AND p.published_at IS NOT NULL
                  AND p.published_at <= CURRENT_TIMESTAMP
                  AND v.status = 'ACTIVE'
                  AND v.base_price > 0
                  AND v.currency = 'VND'
                """, sellableVariant);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst())
                .containsEntry("product_id", sellableProduct)
                .containsEntry("variant_id", sellableVariant)
                .containsEntry("sku", "SKU-SELLABLE")
                .containsEntry("product_status", "ACTIVE")
                .containsEntry("variant_status", "ACTIVE")
                .containsEntry("currency", "VND");
        assertThat(rows.getFirst().get("base_price")).isEqualTo(new BigDecimal("1000.0000"));

        assertThat(countValidationRows(unpublishedProduct)).isZero();
        assertThat(countValidationRows(inactiveVariantProduct)).isZero();
    }

    @Test
    void internalValidationAcceptsOnlyCampaignServiceTokenAndReturnsDirectSnapshot() throws Exception {
        UUID productId = insertProduct("PROD-HTTP", "http-product", "ACTIVE", true);
        UUID variantId = insertVariant(productId, "SKU-HTTP", "ACTIVE", "22990000.0000");

        // This is the approved direct transport body; it must not be wrapped in a public catalog page.
        mockMvc.perform(post("/internal/v1/catalog/variants/campaign-validation")
                        .with(jwt().jwt(token -> token.subject(CAMPAIGN_SUBJECT)
                                .audience(List.of(INTERNAL_AUDIENCE)))
                                .authorities(new SimpleGrantedAuthority("SCOPE_catalog.read")))
                        .header("X-Trace-Id", "trace-product-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":\"" + variantId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.variantId").value(variantId.toString()))
                .andExpect(jsonPath("$.sku").value("SKU-HTTP"))
                .andExpect(jsonPath("$.productStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.variantStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.sellable").value(true))
                .andExpect(jsonPath("$.currency").value("VND"));
    }

    @Test
    void publicAdministratorTokenCannotBeSubstitutedForCampaignServiceIdentity() throws Exception {
        mockMvc.perform(post("/internal/v1/catalog/variants/campaign-validation")
                        .with(jwt().jwt(token -> token.subject("administrator-1")
                                .audience(List.of(PUBLIC_AUDIENCE)))
                                .authorities(new SimpleGrantedAuthority("SCOPE_catalog.read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    private int countValidationRows(UUID productId) {
        return jdbc.queryForObject("""
                SELECT count(*)
                FROM products p
                JOIN product_variants v ON v.product_id = p.id
                WHERE p.id = ?
                  AND p.status = 'ACTIVE'
                  AND p.published_at IS NOT NULL
                  AND p.published_at <= CURRENT_TIMESTAMP
                  AND v.status = 'ACTIVE'
                  AND v.base_price > 0
                  AND v.currency = 'VND'
                """, Integer.class, productId);
    }

    private UUID insertProduct(String code, String slug, String status, boolean published) {
        OffsetDateTime publishedAt = published ? OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1) : null;
        return jdbc.queryForObject("""
                INSERT INTO products (code, slug, name, attributes, status, published_at)
                VALUES (?, ?, ?, CAST('{"fixture":true}' AS jsonb), ?, ?)
                RETURNING id
                """, UUID.class, code, slug, code, status, publishedAt);
    }

    private UUID insertVariant(UUID productId, String sku, String status, String basePrice) {
        return jdbc.queryForObject("""
                INSERT INTO product_variants
                    (product_id, sku, name, attributes, base_price, currency, status, weight_grams)
                VALUES (?, ?, 'Default', CAST('{"fixture":true}' AS jsonb), CAST(? AS numeric), 'VND', ?, 100)
                RETURNING id
                """, UUID.class, productId, sku, basePrice, status);
    }
}
