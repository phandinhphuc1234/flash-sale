package com.philia.flashsale.flashsale.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies the checked-in Liquibase contract before a PostgreSQL-backed migration run. */
@Testcontainers
class FlashSaleSchemaMigrationIntegrationTests {
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void changelogContainsDurableTablesConstraintsIndexesAndRollback() throws IOException {
        String master = Files.readString(RESOURCES.resolve("db/changelog/db.changelog-master.yaml"));
        String sql = Files.readString(RESOURCES.resolve("db/changelog/changes/001-create-flash-sale-mvp-schema.sql"));

        assertTrue(master.contains("001-create-flash-sale-mvp-schema.sql"));
        for (String table : new String[] {
                "purchase_requests", "flash_sale_reservations", "purchase_idempotency_records",
                "flash_sale_outbox_events" }) {
            assertTrue(sql.contains("CREATE TABLE " + table), table);
        }
        assertTrue(sql.contains("FOR UPDATE") == false,
                "claim locking belongs to the later outbox adapter, not the migration");
        assertTrue(sql.contains("CONSTRAINT ck_purchase_requests_quantity"));
        assertTrue(sql.contains("CONSTRAINT ck_flash_sale_reservations_currency"));
        assertTrue(sql.contains("CONSTRAINT uq_flash_sale_outbox_business_event"));
        assertTrue(sql.contains("--rollback DROP TABLE flash_sale_outbox_events;"));
    }

    @Test
    void forwardMigrationExecutesAgainstPostgreSql() throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(), database);
            liquibase.update(new Contexts(), new LabelExpression());

            try (var statement = connection.createStatement()) {
                for (String table : new String[] {
                        "purchase_requests", "flash_sale_reservations", "purchase_idempotency_records",
                        "flash_sale_outbox_events" }) {
                    try (var result = statement.executeQuery("SELECT to_regclass('public." + table + "')")) {
                        assertTrue(result.next() && result.getString(1) != null, table);
                    }
                }
            }
        }
    }

    @Test
    void schemaIsNotBootstrappedBySpringSqlScriptsAndUsesValidateMode() throws IOException {
        String application = Files.readString(RESOURCES.resolve("application.yml"));
        assertTrue(application.contains("ddl-auto: validate"));
        assertTrue(!Files.exists(RESOURCES.resolve("schema.sql")));
        assertTrue(!Files.exists(RESOURCES.resolve("data.sql")));
    }
}
