package com.philia.flashsale.product.catalog;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProductCatalogQueryTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
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
    void browseProductsReturnsOnlyVisibleProductsWithVariantPrices() throws Exception {
        UUID older = insertProduct(
                "PROD-OLD",
                "old-phone",
                "Old Phone",
                "ACTIVE",
                "CURRENT_TIMESTAMP - INTERVAL '2 hours'");
        insertVariant(older, "SKU-OLD", "Old Default", "99000.0000", "ACTIVE", 0);

        UUID newer = insertProduct(
                "PROD-NEW",
                "new-phone",
                "New Phone",
                "ACTIVE",
                "CURRENT_TIMESTAMP - INTERVAL '1 hour'");
        insertVariant(newer, "SKU-NEW", "New Default", "199000.0000", "ACTIVE", 0);

        UUID draft = insertProduct(
                "PROD-DRAFT",
                "draft-phone",
                "Draft Phone",
                "DRAFT",
                "CURRENT_TIMESTAMP - INTERVAL '30 minutes'");
        insertVariant(draft, "SKU-DRAFT", "Draft Default", "1000.0000", "ACTIVE", 0);

        UUID unpublished = insertProduct(
                "PROD-UNPUBLISHED",
                "unpublished-phone",
                "Unpublished Phone",
                "ACTIVE",
                null);
        insertVariant(unpublished, "SKU-UNPUBLISHED", "Unpublished Default", "1000.0000", "ACTIVE", 0);

        UUID inactiveVariantOnly = insertProduct(
                "PROD-INACTIVE-VARIANT",
                "inactive-variant-phone",
                "Inactive Variant Phone",
                "ACTIVE",
                "CURRENT_TIMESTAMP - INTERVAL '10 minutes'");
        insertVariant(inactiveVariantOnly, "SKU-INACTIVE", "Inactive Default", "1000.0000", "INACTIVE", 0);

        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].slug").value("new-phone"))
                .andExpect(jsonPath("$.data[0].variants", hasSize(1)))
                .andExpect(jsonPath("$.data[0].variants[0].basePrice").value("199000.0000"))
                .andExpect(jsonPath("$.data[0].displayPrice").doesNotExist())
                .andExpect(jsonPath("$.data[1].slug").value("old-phone"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.hasNext").value(false));
    }

    @Test
    void browseProductsByCategoryAndCategoriesHandleMissingRecords() throws Exception {
        UUID phones = insertCategory(null, "CAT-PHONES", "phones", "Phones", "ACTIVE", 0);
        insertCategory(null, "CAT-INACTIVE", "inactive", "Inactive", "INACTIVE", 1);
        UUID laptops = insertCategory(null, "CAT-LAPTOPS", "laptops", "Laptops", "ACTIVE", 2);
        insertCategory(null, "CAT-EMPTY", "empty", "Empty", "ACTIVE", 3);

        UUID phone = insertProduct(
                "PROD-PHONE",
                "phone",
                "Phone",
                "ACTIVE",
                "CURRENT_TIMESTAMP - INTERVAL '1 hour'");
        insertVariant(phone, "SKU-PHONE", "Phone Default", "100000.0000", "ACTIVE", 0);
        insertProductCategory(phone, phones, true, 0);

        UUID laptop = insertProduct(
                "PROD-LAPTOP",
                "laptop",
                "Laptop",
                "ACTIVE",
                "CURRENT_TIMESTAMP - INTERVAL '2 hours'");
        insertVariant(laptop, "SKU-LAPTOP", "Laptop Default", "200000.0000", "ACTIVE", 0);
        insertProductCategory(laptop, laptops, true, 0);

        mockMvc.perform(get("/api/v1/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].slug").value("phones"))
                .andExpect(jsonPath("$.data[1].slug").value("laptops"))
                .andExpect(jsonPath("$.data[2].slug").value("empty"));

        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("categorySlug", "phones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].slug").value("phone"));

        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("categorySlug", "empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.page.totalElements").value(0));

        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("categorySlug", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void viewProductReturnsVisibleDetailAndHidesNonVisibleProducts() throws Exception {
        UUID category = insertCategory(null, "CAT-DETAIL", "detail", "Detail", "ACTIVE", 0);
        UUID product = insertProduct(
                "PROD-DETAIL",
                "detail-phone",
                "Detail Phone",
                "ACTIVE",
                "CURRENT_TIMESTAMP - INTERVAL '1 hour'");
        insertVariant(product, "SKU-ACTIVE", "Active Variant", "123000.0000", "ACTIVE", 0);
        insertVariant(product, "SKU-INACTIVE-DETAIL", "Inactive Variant", "456000.0000", "INACTIVE", 1);
        insertProductCategory(product, category, true, 0);
        insertMedia(product, null, "IMAGE", "https://cdn.example.test/detail.webp", "Detail image", "ACTIVE", 0);
        insertMedia(product, null, "IMAGE", "https://cdn.example.test/inactive.webp", "Inactive image", "INACTIVE", 1);

        UUID hidden = insertProduct(
                "PROD-HIDDEN",
                "hidden-phone",
                "Hidden Phone",
                "ACTIVE",
                "CURRENT_TIMESTAMP - INTERVAL '1 hour'");
        insertVariant(hidden, "SKU-HIDDEN", "Hidden Variant", "1000.0000", "INACTIVE", 0);

        mockMvc.perform(get("/api/v1/catalog/products/detail-phone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("detail-phone"))
                .andExpect(jsonPath("$.variants", hasSize(1)))
                .andExpect(jsonPath("$.variants[0].sku").value("SKU-ACTIVE"))
                .andExpect(jsonPath("$.variants[0].basePrice").value("123000.0000"))
                .andExpect(jsonPath("$.displayPrice").doesNotExist())
                .andExpect(jsonPath("$.categories", hasSize(1)))
                .andExpect(jsonPath("$.categories[0].primary").value(true))
                .andExpect(jsonPath("$.media", hasSize(1)))
                .andExpect(jsonPath("$.media[0].url").value("https://cdn.example.test/detail.webp"));

        mockMvc.perform(get("/api/v1/catalog/products/hidden-phone"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/catalog/products/missing-phone"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void browseProductsRejectsInvalidPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATALOG_REQUEST"));
    }

    private UUID insertCategory(
            UUID parentId,
            String code,
            String slug,
            String name,
            String status,
            int sortOrder) {
        return jdbc.queryForObject("""
                INSERT INTO categories
                    (parent_id, code, slug, name, status, sort_order)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class, parentId, code, slug, name, status, sortOrder);
    }

    private UUID insertProduct(
            String code,
            String slug,
            String name,
            String status,
            String publishedAtExpression) {
        String publishedAt = publishedAtExpression == null ? "NULL" : publishedAtExpression;
        return jdbc.queryForObject("""
                INSERT INTO products
                    (code, slug, name, short_description, description, attributes,
                     status, published_at)
                VALUES (?, ?, ?, ?, ?, CAST('{"fixture":true}' AS jsonb), ?, %s)
                RETURNING id
                """.formatted(publishedAt),
                UUID.class,
                code,
                slug,
                name,
                name + " short",
                name + " description",
                status);
    }

    private UUID insertVariant(
            UUID productId,
            String sku,
            String name,
            String basePrice,
            String status,
            int sortOrder) {
        return jdbc.queryForObject("""
                INSERT INTO product_variants
                    (product_id, sku, name, attributes, base_price, currency,
                     status, sort_order)
                VALUES (?, ?, ?, CAST('{"fixture":true}' AS jsonb),
                        CAST(? AS numeric), 'VND', ?, ?)
                RETURNING id
                """, UUID.class, productId, sku, name, basePrice, status, sortOrder);
    }

    private void insertProductCategory(
            UUID productId,
            UUID categoryId,
            boolean primary,
            int sortOrder) {
        jdbc.update("""
                INSERT INTO product_categories
                    (product_id, category_id, is_primary, sort_order)
                VALUES (?, ?, ?, ?)
                """, productId, categoryId, primary, sortOrder);
    }

    private void insertMedia(
            UUID productId,
            UUID variantId,
            String mediaType,
            String url,
            String altText,
            String status,
            int sortOrder) {
        jdbc.update("""
                INSERT INTO product_media
                    (product_id, variant_id, media_type, url, alt_text,
                     status, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, productId, variantId, mediaType, url, altText, status, sortOrder);
    }
}
