package com.philia.flashsale.product.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.product.catalog.application.port.out.LoadCatalogPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies the Product-owned batch projection against the real schema and query adapter. */
@SpringBootTest
@Testcontainers
class ProductVariantDisplayPersistenceTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("product_db")
            .withUsername("product")
            .withPassword("product");

    @Autowired
    private LoadCatalogPort loadCatalogPort;

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
        jdbc.execute("TRUNCATE TABLE product_media, product_variants, products CASCADE");
    }

    @Test
    void returnsCurrentDisplayFieldsInRequestOrderAndRepresentsMissingIds() {
        UUID productId = insertProduct("DISPLAY-PRODUCT", "display-product", "Display Product");
        UUID activeVariant = insertVariant(productId, "DISPLAY-ACTIVE", "Black / M", "299000.0000", "ACTIVE");
        UUID inactiveVariant = insertVariant(productId, "DISPLAY-INACTIVE", "White / M", "199000.0000", "INACTIVE");
        jdbc.update("""
                INSERT INTO product_media (product_id, media_type, url, status, sort_order)
                VALUES (?, 'IMAGE', ?, 'ACTIVE', 0)
                """, productId, "https://cdn.example.test/display.webp");
        UUID missingVariant = UUID.randomUUID();

        var results = loadCatalogPort.loadVariantDisplays(List.of(inactiveVariant, activeVariant, missingVariant));

        assertThat(results).extracting(result -> result.variantId())
                .containsExactly(inactiveVariant, activeVariant, missingVariant);
        assertThat(results.get(0).found()).isTrue();
        assertThat(results.get(0).sellable()).isFalse();
        assertThat(results.get(1).found()).isTrue();
        assertThat(results.get(1).sellable()).isTrue();
        assertThat(results.get(1).basePrice()).isEqualByComparingTo(new BigDecimal("299000.0000"));
        assertThat(results.get(1).primaryImageUrl()).isEqualTo("https://cdn.example.test/display.webp");
        assertThat(results.get(2).found()).isFalse();
        assertThat(results.get(2).sellable()).isFalse();
        assertThat(results.get(2).productId()).isNull();
    }

    private UUID insertProduct(String code, String slug, String name) {
        return jdbc.queryForObject("""
                INSERT INTO products (code, slug, name, attributes, status, published_at)
                VALUES (?, ?, ?, CAST('{}' AS jsonb), 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '1 hour')
                RETURNING id
                """, UUID.class, code, slug, name);
    }

    private UUID insertVariant(
            UUID productId, String sku, String name, String basePrice, String status) {
        return jdbc.queryForObject("""
                INSERT INTO product_variants
                    (product_id, sku, name, attributes, base_price, currency, status)
                VALUES (?, ?, ?, CAST('{}' AS jsonb), CAST(? AS numeric), 'VND', ?)
                RETURNING id
                """, UUID.class, productId, sku, name, basePrice, status);
    }
}
