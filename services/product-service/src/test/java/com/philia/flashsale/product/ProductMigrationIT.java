package com.philia.flashsale.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ProductMigrationIT {

    private static final String CHANGELOG = "classpath:/db/changelog/db.changelog-master.yaml";

    private static final Set<String> EXPECTED_PUBLIC_TABLES = Set.of(
            "categories",
            "products",
            "product_variants",
            "product_categories",
            "product_media",
            "product_admin_idempotency_keys",
            "product_admin_audit_logs",
            "databasechangelog",
            "databasechangeloglock");

    private static final Set<String> REQUIRED_INDEXES = Set.of(
            "idx_categories_parent_sort",
            "idx_products_status_published",
            "idx_product_variants_product_sort",
            "idx_product_categories_category_sort_product",
            "uq_product_categories_one_primary",
            "idx_product_media_product_variant_sort",
            "idx_product_admin_idempotency_expires",
            "idx_product_admin_audit_product_created",
            "idx_product_admin_audit_actor_created",
            "idx_product_admin_audit_trace");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("product_db")
                    .withUsername("product")
                    .withPassword("product");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateDatabase() throws Exception {
        DriverManagerDataSource configuredDataSource = new DriverManagerDataSource();
        configuredDataSource.setDriverClassName("org.postgresql.Driver");
        configuredDataSource.setUrl(POSTGRES.getJdbcUrl());
        configuredDataSource.setUsername(POSTGRES.getUsername());
        configuredDataSource.setPassword(POSTGRES.getPassword());

        dataSource = configuredDataSource;
        jdbc = new JdbcTemplate(dataSource);
        newLiquibase().afterPropertiesSet();
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
    void migrationCreatesBusinessTablesSupportTablesAndLiquibaseLedger() {
        assertThat(publicTables()).containsExactlyInAnyOrderElementsOf(EXPECTED_PUBLIC_TABLES);

        List<Map<String, Object>> changes = jdbc.queryForList("""
                SELECT id, author, exectype, orderexecuted
                FROM databasechangelog
                ORDER BY orderexecuted
                """);

        assertThat(changes).hasSize(2);
        assertThat(changes.getFirst())
                .containsEntry("id", "001-create-product-catalog-schema")
                .containsEntry("author", "philia")
                .containsEntry("exectype", "EXECUTED")
                .containsEntry("orderexecuted", 1);
        assertThat(changes.get(1))
                .containsEntry("id", "002-create-product-admin-support")
                .containsEntry("author", "philia")
                .containsEntry("exectype", "EXECUTED")
                .containsEntry("orderexecuted", 2);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM databasechangeloglock",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT locked FROM databasechangeloglock WHERE id = 1",
                        Boolean.class))
                .isFalse();
    }

    @Test
    void migrationCreatesRequiredIndexesAndRestrictedForeignKeys() {
        List<String> indexNames = jdbc.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                """, String.class);

        assertThat(indexNames).containsAll(REQUIRED_INDEXES);
        assertThat(indexColumns("idx_product_categories_category_sort_product"))
                .isEqualTo("category_id,sort_order,product_id");

        String primaryCategoryIndex = jdbc.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = 'uq_product_categories_one_primary'
                """, String.class);
        assertThat(primaryCategoryIndex)
                .contains("CREATE UNIQUE INDEX")
                .contains("(product_id)")
                .containsIgnoringCase("WHERE")
                .contains("is_primary");

        assertThat(jdbc.queryForObject("""
                        SELECT count(*)
                        FROM pg_constraint
                        WHERE contype = 'f'
                          AND connamespace = 'public'::regnamespace
                        """, Integer.class))
                .isEqualTo(8);
        assertThat(jdbc.queryForObject("""
                        SELECT count(*)
                        FROM pg_constraint
                        WHERE contype = 'f'
                          AND connamespace = 'public'::regnamespace
                          AND confdeltype <> 'r'
                        """, Integer.class))
                .isZero();
    }

    @Test
    void mediaForeignKeyUsesProductAndVariantOwnershipTogether() {
        List<Map<String, Object>> compositeForeignKeys = jdbc.queryForList("""
                SELECT
                    string_agg(source_attribute.attname, ',' ORDER BY key_position.position)
                        AS source_columns,
                    string_agg(target_attribute.attname, ',' ORDER BY key_position.position)
                        AS target_columns
                FROM pg_constraint constraint_definition
                CROSS JOIN LATERAL
                    generate_subscripts(constraint_definition.conkey, 1)
                        AS key_position(position)
                JOIN pg_attribute source_attribute
                  ON source_attribute.attrelid = constraint_definition.conrelid
                 AND source_attribute.attnum =
                        constraint_definition.conkey[key_position.position]
                JOIN pg_attribute target_attribute
                  ON target_attribute.attrelid = constraint_definition.confrelid
                 AND target_attribute.attnum =
                        constraint_definition.confkey[key_position.position]
                WHERE constraint_definition.contype = 'f'
                  AND constraint_definition.conrelid = 'product_media'::regclass
                  AND constraint_definition.confrelid = 'product_variants'::regclass
                GROUP BY constraint_definition.oid
                """);

        assertThat(compositeForeignKeys).hasSize(1);
        assertThat(compositeForeignKeys.getFirst())
                .containsEntry("source_columns", "product_id,variant_id")
                .containsEntry("target_columns", "product_id,id");
    }

    @Test
    void validCatalogFixtureCanBeStored() {
        UUID rootCategory = insertCategory(null, "CAT-ROOT", "root", "Root");
        UUID childCategory = insertCategory(
                rootCategory,
                "CAT-CHILD",
                "child",
                "Child");
        UUID product = insertProduct("PROD-001", "product-001", "Product 001");
        UUID variant = insertVariant(
                product,
                "SKU-001",
                "BARCODE-001",
                "Default",
                "99000.0000",
                "VND");

        jdbc.update("""
                INSERT INTO product_categories
                    (product_id, category_id, is_primary, sort_order)
                VALUES (?, ?, TRUE, 0)
                """, product, childCategory);

        jdbc.update("""
                INSERT INTO product_media
                    (product_id, media_type, url, alt_text, mime_type,
                     width_px, height_px, file_size_bytes, sort_order, status)
                VALUES (?, 'IMAGE', ?, 'Product image', 'image/webp',
                        1200, 800, 1024, 0, 'ACTIVE')
                """, product, "https://cdn.example.test/product.webp");

        jdbc.update("""
                INSERT INTO product_media
                    (product_id, variant_id, media_type, url, mime_type,
                     file_size_bytes, sort_order, status)
                VALUES (?, ?, 'VIDEO', ?, 'video/mp4', 2048, 1, 'INACTIVE')
                """, product, variant, "https://cdn.example.test/product.mp4");

        assertThat(count("categories")).isEqualTo(2);
        assertThat(count("products")).isEqualTo(1);
        assertThat(count("product_variants")).isEqualTo(1);
        assertThat(count("product_categories")).isEqualTo(1);
        assertThat(count("product_media")).isEqualTo(2);
    }

    @Test
    void adminSupportTablesStoreReplayAndAuditEvidence() {
        UUID product = insertProduct("PROD-ADMIN", "product-admin", "Product Admin");

        jdbc.update("""
                INSERT INTO product_admin_idempotency_keys
                    (actor_id, idempotency_key, command_name, request_hash,
                     target_product_id, http_status, response_body, expires_at)
                VALUES ('admin-1', 'key-1', 'CREATE_PRODUCT_DRAFT', 'hash-1',
                        ?, 201,
                        CAST('{"id":"%s","status":"DRAFT","version":0}' AS jsonb),
                        CURRENT_TIMESTAMP + INTERVAL '7 days')
                """.formatted(product), product);

        jdbc.update("""
                INSERT INTO product_admin_audit_logs
                    (actor_id, trace_id, command_name, target_product_id,
                     outcome, product_version)
                VALUES ('admin-1', 'trace-1', 'CREATE_PRODUCT_DRAFT', ?,
                        'SUCCESS', 0)
                """, product);

        assertThat(count("product_admin_idempotency_keys")).isEqualTo(1);
        assertThat(count("product_admin_audit_logs")).isEqualTo(1);

        assertRejected("""
                INSERT INTO product_admin_idempotency_keys
                    (actor_id, idempotency_key, command_name, request_hash,
                     http_status, response_body, expires_at)
                VALUES ('admin-1', 'key-1', 'CREATE_PRODUCT_DRAFT', 'hash-1',
                        201, CAST('{}' AS jsonb),
                        CURRENT_TIMESTAMP + INTERVAL '7 days')
                """);
        assertRejected("""
                INSERT INTO product_admin_idempotency_keys
                    (actor_id, idempotency_key, command_name, request_hash,
                     http_status, response_body, expires_at)
                VALUES ('admin-2', 'key-bad', 'CREATE_PRODUCT_DRAFT', 'hash-2',
                        700, CAST('{}' AS jsonb),
                        CURRENT_TIMESTAMP + INTERVAL '7 days')
                """);
        assertRejected("""
                INSERT INTO product_admin_audit_logs
                    (actor_id, trace_id, command_name, outcome)
                VALUES ('admin-1', 'trace-1', 'CREATE_PRODUCT_DRAFT', 'UNKNOWN')
                """);
    }

    @Test
    void invalidMoneyIsRejected() {
        UUID product = insertProduct("PROD-MONEY", "product-money", "Money");

        assertRejected("""
                INSERT INTO product_variants
                    (product_id, sku, name, base_price, currency)
                VALUES (?, 'SKU-NEGATIVE', 'Negative', -0.0001, 'VND')
                """, product);
        assertRejected("""
                INSERT INTO product_variants
                    (product_id, sku, name, base_price, currency)
                VALUES (?, 'SKU-USD', 'USD', 1.0000, 'USD')
                """, product);
        assertRejected("""
                INSERT INTO product_variants
                    (product_id, sku, name, base_price, currency)
                VALUES (?, 'SKU-NO-AMOUNT', 'No amount', NULL, 'VND')
                """, product);
        assertRejected("""
                INSERT INTO product_variants
                    (product_id, sku, name, base_price, currency)
                VALUES (?, 'SKU-NO-CURRENCY', 'No currency', 1.0000, NULL)
                """, product);
    }

    @Test
    void negativeOrdersVersionsAndPhysicalMeasurementsAreRejected() {
        UUID category = insertCategory(null, "CAT-ORDER", "category-order", "Category");
        UUID product = insertProduct("PROD-ORDER", "product-order", "Product");
        UUID variant = insertVariant(
                product,
                "SKU-ORDER",
                null,
                "Variant",
                "100.0000",
                "VND");

        assertRejected("""
                INSERT INTO categories (code, slug, name, sort_order)
                VALUES ('CAT-NEGATIVE-ORDER', 'negative-order', 'Negative', -1)
                """);
        assertRejected("""
                INSERT INTO product_variants
                    (product_id, sku, name, base_price, currency, sort_order)
                VALUES (?, 'SKU-NEGATIVE-ORDER', 'Negative', 1.0000, 'VND', -1)
                """, product);
        assertRejected("""
                INSERT INTO product_categories
                    (product_id, category_id, sort_order)
                VALUES (?, ?, -1)
                """, product, category);
        assertRejected("""
                INSERT INTO product_media
                    (product_id, media_type, url, sort_order)
                VALUES (?, 'IMAGE', 'https://example.test/order.png', -1)
                """, product);

        assertRejected("""
                INSERT INTO categories (code, slug, name, version)
                VALUES ('CAT-NEGATIVE-VERSION', 'negative-version', 'Negative', -1)
                """);
        assertRejected("""
                INSERT INTO products (code, slug, name, version)
                VALUES ('PROD-NEGATIVE-VERSION', 'negative-product-version',
                        'Negative', -1)
                """);
        assertRejected("""
                INSERT INTO product_variants
                    (product_id, sku, name, base_price, currency, version)
                VALUES (?, 'SKU-NEGATIVE-VERSION', 'Negative', 1.0000, 'VND', -1)
                """, product);

        assertRejected("""
                INSERT INTO product_variants
                    (product_id, sku, name, base_price, currency, weight_grams)
                VALUES (?, 'SKU-ZERO-WEIGHT', 'Weight', 1.0000, 'VND', 0)
                """, product);
        assertRejected("""
                INSERT INTO product_media
                    (product_id, variant_id, media_type, url, width_px)
                VALUES (?, ?, 'IMAGE', 'https://example.test/width.png', 0)
                """, product, variant);
        assertRejected("""
                INSERT INTO product_media
                    (product_id, variant_id, media_type, url, height_px)
                VALUES (?, ?, 'IMAGE', 'https://example.test/height.png', -1)
                """, product, variant);
        assertRejected("""
                INSERT INTO product_media
                    (product_id, variant_id, media_type, url, file_size_bytes)
                VALUES (?, ?, 'IMAGE', 'https://example.test/size.png', 0)
                """, product, variant);
    }

    @Test
    void invalidLifecycleStatusAndMediaTypeAreRejected() {
        UUID product = insertProduct("PROD-STATUS", "product-status", "Product");
        UUID variant = insertVariant(
                product,
                "SKU-STATUS",
                null,
                "Variant",
                "100.0000",
                "VND");

        assertRejected("""
                INSERT INTO categories (code, slug, name, status)
                VALUES ('CAT-BAD-STATUS', 'bad-category-status', 'Category', 'DRAFT')
                """);
        assertRejected("""
                INSERT INTO products (code, slug, name, status)
                VALUES ('PROD-BAD-STATUS', 'bad-product-status', 'Product', 'DELETED')
                """);
        assertRejected("""
                INSERT INTO product_variants
                    (product_id, sku, name, base_price, currency, status)
                VALUES (?, 'SKU-BAD-STATUS', 'Variant', 1.0000, 'VND', 'DRAFT')
                """, product);
        assertRejected("""
                INSERT INTO product_media
                    (product_id, variant_id, media_type, url, status)
                VALUES (?, ?, 'IMAGE', 'https://example.test/status.png', 'DRAFT')
                """, product, variant);
        assertRejected("""
                INSERT INTO product_media
                    (product_id, variant_id, media_type, url)
                VALUES (?, ?, 'AUDIO', 'https://example.test/audio.mp3')
                """, product, variant);
    }

    @Test
    void blankKeysNonObjectAttributesAndBlankMediaUrlAreRejected() {
        UUID product = insertProduct("PROD-BLANK", "product-blank", "Product");

        assertRejected("""
                INSERT INTO categories (code, slug, name)
                VALUES ('   ', 'blank-code', 'Category')
                """);
        assertRejected("""
                INSERT INTO categories (code, slug, name)
                VALUES ('CAT-BLANK-SLUG', '   ', 'Category')
                """);
        assertRejected("""
                INSERT INTO categories (code, slug, name)
                VALUES ('CAT-BLANK-NAME', 'blank-name', '   ')
                """);
        assertRejected("""
                INSERT INTO products (code, slug, name)
                VALUES ('   ', 'blank-product-code', 'Product')
                """);
        assertRejected("""
                INSERT INTO products (code, slug, name)
                VALUES ('PROD-BLANK-SLUG', '   ', 'Product')
                """);
        assertRejected("""
                INSERT INTO products (code, slug, name, attributes)
                VALUES ('PROD-BAD-JSON', 'bad-product-json', 'Product',
                        CAST('[]' AS jsonb))
                """);
        assertRejected("""
                INSERT INTO product_variants
                    (product_id, sku, name, base_price, currency)
                VALUES (?, '   ', 'Variant', 1.0000, 'VND')
                """, product);
        assertRejected("""
                INSERT INTO product_variants
                    (product_id, sku, barcode, name, base_price, currency)
                VALUES (?, 'SKU-BLANK-BARCODE', '   ', 'Variant', 1.0000, 'VND')
                """, product);
        assertRejected("""
                INSERT INTO product_variants
                    (product_id, sku, name, attributes, base_price, currency)
                VALUES (?, 'SKU-BAD-JSON', 'Variant', CAST('[]' AS jsonb),
                        1.0000, 'VND')
                """, product);
        assertRejected("""
                INSERT INTO product_media (product_id, media_type, url)
                VALUES (?, 'IMAGE', '   ')
                """, product);
    }

    @Test
    void duplicateNaturalKeysMembershipAndPrimaryCategoryAreRejected() {
        UUID categoryOne = insertCategory(null, "CAT-DUP-1", "category-dup-1", "One");
        UUID categoryTwo = insertCategory(null, "CAT-DUP-2", "category-dup-2", "Two");
        UUID product = insertProduct("PROD-DUP-1", "product-dup-1", "Product");
        insertVariant(
                product,
                "SKU-DUP-1",
                "BARCODE-DUP-1",
                "Variant",
                "100.0000",
                "VND");

        assertRejected("""
                INSERT INTO categories (code, slug, name)
                VALUES ('CAT-DUP-1', 'category-unique-slug', 'Duplicate code')
                """);
        assertRejected("""
                INSERT INTO categories (code, slug, name)
                VALUES ('CAT-UNIQUE-CODE', 'category-dup-1', 'Duplicate slug')
                """);
        assertRejected("""
                INSERT INTO products (code, slug, name)
                VALUES ('PROD-DUP-1', 'product-unique-slug', 'Duplicate code')
                """);
        assertRejected("""
                INSERT INTO products (code, slug, name)
                VALUES ('PROD-UNIQUE-CODE', 'product-dup-1', 'Duplicate slug')
                """);
        assertRejected("""
                INSERT INTO product_variants
                    (product_id, sku, barcode, name, base_price, currency)
                VALUES (?, 'SKU-DUP-1', 'BARCODE-UNIQUE', 'Duplicate SKU',
                        1.0000, 'VND')
                """, product);
        assertRejected("""
                INSERT INTO product_variants
                    (product_id, sku, barcode, name, base_price, currency)
                VALUES (?, 'SKU-UNIQUE', 'BARCODE-DUP-1', 'Duplicate barcode',
                        1.0000, 'VND')
                """, product);

        jdbc.update("""
                INSERT INTO product_categories
                    (product_id, category_id, is_primary)
                VALUES (?, ?, TRUE)
                """, product, categoryOne);
        assertRejected("""
                INSERT INTO product_categories
                    (product_id, category_id, is_primary)
                VALUES (?, ?, FALSE)
                """, product, categoryOne);
        assertRejected("""
                INSERT INTO product_categories
                    (product_id, category_id, is_primary)
                VALUES (?, ?, TRUE)
                """, product, categoryTwo);
    }

    @Test
    void orphanReferencesAreRejected() {
        UUID category = insertCategory(null, "CAT-FK", "category-fk", "Category");
        UUID product = insertProduct("PROD-FK", "product-fk", "Product");

        assertRejected("""
                INSERT INTO categories (parent_id, code, slug, name)
                VALUES (?, 'CAT-ORPHAN', 'category-orphan', 'Orphan')
                """, UUID.randomUUID());
        assertRejected("""
                INSERT INTO product_variants
                    (product_id, sku, name, base_price, currency)
                VALUES (?, 'SKU-ORPHAN', 'Orphan', 1.0000, 'VND')
                """, UUID.randomUUID());
        assertRejected("""
                INSERT INTO product_categories (product_id, category_id)
                VALUES (?, ?)
                """, UUID.randomUUID(), category);
        assertRejected("""
                INSERT INTO product_categories (product_id, category_id)
                VALUES (?, ?)
                """, product, UUID.randomUUID());
        assertRejected("""
                INSERT INTO product_media (product_id, media_type, url)
                VALUES (?, 'IMAGE', 'https://example.test/orphan-product.png')
                """, UUID.randomUUID());
        assertRejected("""
                INSERT INTO product_media
                    (product_id, variant_id, media_type, url)
                VALUES (?, ?, 'IMAGE', 'https://example.test/orphan-variant.png')
                """, product, UUID.randomUUID());
    }

    @Test
    void mediaCannotReferenceVariantOwnedByAnotherProduct() {
        UUID firstProduct = insertProduct("PROD-OWNER-1", "product-owner-1", "One");
        UUID secondProduct = insertProduct("PROD-OWNER-2", "product-owner-2", "Two");
        UUID firstVariant = insertVariant(
                firstProduct,
                "SKU-OWNER-1",
                null,
                "Variant",
                "100.0000",
                "VND");

        assertRejected("""
                INSERT INTO product_media
                    (product_id, variant_id, media_type, url)
                VALUES (?, ?, 'IMAGE', 'https://example.test/cross-product.png')
                """, secondProduct, firstVariant);
    }

    @Test
    void secondLiquibaseInvocationIsIdempotent() throws Exception {
        SchemaSnapshot beforeSecondRun = schemaSnapshot();

        newLiquibase().afterPropertiesSet();

        assertThat(schemaSnapshot()).isEqualTo(beforeSecondRun);
        assertThat(jdbc.queryForObject(
                        "SELECT locked FROM databasechangeloglock WHERE id = 1",
                        Boolean.class))
                .isFalse();
    }

    @Test
    void changesetDocumentsSafeReverseDependencyRollback() throws IOException {
        String migrationSql;
        try (var input = new ClassPathResource(
                "db/changelog/changes/001-create-product-catalog-schema.sql")
                .getInputStream()) {
            migrationSql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(migrationSql)
                .containsSubsequence(
                        "--rollback DROP TABLE product_media;",
                        "--rollback DROP TABLE product_categories;",
                        "--rollback DROP TABLE product_variants;",
                        "--rollback DROP TABLE products;",
                        "--rollback DROP TABLE categories;")
                .doesNotContainIgnoringCase("CASCADE");
    }

    private static SpringLiquibase newLiquibase() {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(CHANGELOG);
        liquibase.setShouldRun(true);
        return liquibase;
    }

    private List<String> publicTables() {
        return jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """, String.class);
    }

    private String indexColumns(String indexName) {
        return jdbc.queryForObject("""
                SELECT string_agg(attribute.attname, ',' ORDER BY key_column.ordinality)
                FROM pg_class index_definition
                JOIN pg_index index_metadata
                  ON index_metadata.indexrelid = index_definition.oid
                JOIN pg_class indexed_table
                  ON indexed_table.oid = index_metadata.indrelid
                CROSS JOIN LATERAL
                    unnest(index_metadata.indkey)
                        WITH ORDINALITY AS key_column(attnum, ordinality)
                JOIN pg_attribute attribute
                  ON attribute.attrelid = indexed_table.oid
                 AND attribute.attnum = key_column.attnum
                WHERE index_definition.relname = ?
                  AND index_definition.relnamespace = 'public'::regnamespace
                """, String.class, indexName);
    }

    private UUID insertCategory(
            UUID parentId,
            String code,
            String slug,
            String name) {
        return jdbc.queryForObject("""
                INSERT INTO categories (parent_id, code, slug, name)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, UUID.class, parentId, code, slug, name);
    }

    private UUID insertProduct(String code, String slug, String name) {
        return jdbc.queryForObject("""
                INSERT INTO products
                    (code, slug, name, attributes, status, published_at)
                VALUES (?, ?, ?, CAST('{"fixture":true}' AS jsonb),
                        'ACTIVE', CURRENT_TIMESTAMP)
                RETURNING id
                """, UUID.class, code, slug, name);
    }

    private UUID insertVariant(
            UUID productId,
            String sku,
            String barcode,
            String name,
            String basePrice,
            String currency) {
        return jdbc.queryForObject("""
                INSERT INTO product_variants
                    (product_id, sku, barcode, name, attributes,
                     base_price, currency, weight_grams, status)
                VALUES (?, ?, ?, ?, CAST('{"fixture":true}' AS jsonb),
                        CAST(? AS numeric), ?, 100, 'ACTIVE')
                RETURNING id
                """, UUID.class, productId, sku, barcode, name, basePrice, currency);
    }

    private void assertRejected(String sql, Object... arguments) {
        assertThatThrownBy(() -> jdbc.update(sql, arguments))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private int count(String tableName) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tableName, Integer.class);
    }

    private SchemaSnapshot schemaSnapshot() {
        return new SchemaSnapshot(
                jdbc.queryForObject("""
                        SELECT count(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_type = 'BASE TABLE'
                        """, Integer.class),
                jdbc.queryForObject("""
                        SELECT count(*)
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                        """, Integer.class),
                jdbc.queryForObject("""
                        SELECT count(*)
                        FROM pg_constraint
                        WHERE connamespace = 'public'::regnamespace
                        """, Integer.class),
                jdbc.queryForObject(
                        "SELECT count(*) FROM databasechangelog",
                        Integer.class),
                jdbc.queryForObject(
                        "SELECT max(orderexecuted) FROM databasechangelog",
                        Integer.class));
    }

    private record SchemaSnapshot(
            int tableCount,
            int indexCount,
            int constraintCount,
            int changelogCount,
            int maxExecutionOrder) {
    }
}
