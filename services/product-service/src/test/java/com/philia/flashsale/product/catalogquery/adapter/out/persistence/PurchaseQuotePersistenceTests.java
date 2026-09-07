package com.philia.flashsale.product.catalogquery.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.product.catalogquery.application.port.out.LoadPurchaseQuotePort;
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

/** Proves that the checkout projection is current, deterministic, and keeps missing rows opaque. */
@SpringBootTest
@Testcontainers
class PurchaseQuotePersistenceTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("product_db")
            .withUsername("product")
            .withPassword("product");

    @Autowired
    private LoadPurchaseQuotePort loadPurchaseQuotePort;

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
    void returnsCurrentPriceCurrencyVersionAndAStableUnsellableOrMissingDecision() {
        UUID productId = insertProduct("QUOTE-PRODUCT", "quote-product", "Quote Product");
        UUID sellable = insertVariant(productId, "QUOTE-ACTIVE", "Black / M", "179000.0000", "ACTIVE", 7);
        UUID unsellable = insertVariant(productId, "QUOTE-INACTIVE", "White / M", "99000.0000", "INACTIVE", 8);
        UUID missing = UUID.randomUUID();

        var quotes = loadPurchaseQuotePort.loadPurchaseQuotes(List.of(unsellable, missing, sellable));

        assertThat(quotes).extracting(quote -> quote.variantId()).containsExactly(unsellable, missing, sellable);
        assertThat(quotes.get(0).found()).isTrue();
        assertThat(quotes.get(0).sellable()).isFalse();
        assertThat(quotes.get(0).unavailableReason()).isEqualTo("NOT_SELLABLE");
        assertThat(quotes.get(0).unitPrice()).isEqualByComparingTo("99000.0000");
        assertThat(quotes.get(0).catalogVersion()).isEqualTo(8L);
        assertThat(quotes.get(1).found()).isFalse();
        assertThat(quotes.get(1).productId()).isNull();
        assertThat(quotes.get(1).unitPrice()).isNull();
        assertThat(quotes.get(2).sellable()).isTrue();
        assertThat(quotes.get(2).unitPrice()).isEqualByComparingTo(new BigDecimal("179000.0000"));
        assertThat(quotes.get(2).currency()).isEqualTo("VND");
        assertThat(quotes.get(2).catalogVersion()).isEqualTo(7L);
    }

    private UUID insertProduct(String code, String slug, String name) {
        return jdbc.queryForObject("""
                INSERT INTO products (code, slug, name, attributes, status, published_at)
                VALUES (?, ?, ?, CAST('{}' AS jsonb), 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '1 hour')
                RETURNING id
                """, UUID.class, code, slug, name);
    }

    private UUID insertVariant(
            UUID productId, String sku, String name, String basePrice, String status, long version) {
        return jdbc.queryForObject("""
                INSERT INTO product_variants
                    (product_id, sku, name, attributes, base_price, currency, status, version)
                VALUES (?, ?, ?, CAST('{}' AS jsonb), CAST(? AS numeric), 'VND', ?, ?)
                RETURNING id
                """, UUID.class, productId, sku, name, basePrice, status, version);
    }
}
