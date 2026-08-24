package com.philia.flashsale.flashsale.integration;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.util.UUID;
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
        String finalization = Files.readString(
                RESOURCES.resolve("db/changelog/changes/002-add-reservation-finalization.sql"));

        assertTrue(master.contains("001-create-flash-sale-mvp-schema.sql"));
        assertTrue(master.contains("002-add-reservation-finalization.sql"));
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
        assertTrue(finalization.contains("redis_reconciled_at"));
        assertTrue(finalization.contains("finalized_at"));
        assertTrue(finalization.contains("reservation_command_inbox"));
        assertTrue(finalization.contains("PurchaseReservationConfirmed"));
        assertTrue(finalization.contains("PurchaseReservationReleased"));
        assertTrue(finalization.contains("causation_id"));
        assertTrue(finalization.contains("uq_flash_sale_outbox_legacy_accepted"));
        assertTrue(finalization.contains("uq_flash_sale_outbox_reservation_outcome_causation"));
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
                        "flash_sale_outbox_events", "reservation_command_inbox" }) {
                    try (var result = statement.executeQuery("SELECT to_regclass('public." + table + "')")) {
                        assertTrue(result.next() && result.getString(1) != null, table);
                    }
                }
                assertTrue(columnExists(connection, "flash_sale_reservations", "redis_reconciled_at"));
                assertTrue(columnExists(connection, "flash_sale_reservations", "finalized_at"));
                assertTrue(columnExists(connection, "flash_sale_outbox_events", "causation_id"));

                UUID aggregateId = UUID.randomUUID();
                insertOutbox(connection, UUID.randomUUID(), aggregateId, "PurchaseAccepted", null);
                connection.commit();
                try (var rejected = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
                    assertThrows(java.sql.SQLException.class,
                            () -> insertOutbox(rejected, UUID.randomUUID(), aggregateId,
                                    "PurchaseAccepted", null));
                }

                UUID causationId = UUID.randomUUID();
                insertOutbox(connection, UUID.randomUUID(), aggregateId,
                        "PurchaseReservationConfirmed", causationId);
                connection.commit();
                try (var rejected = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
                    assertThrows(java.sql.SQLException.class,
                            () -> insertOutbox(rejected, UUID.randomUUID(), aggregateId,
                                    "PurchaseReservationReleased", causationId));
                }
            }
        }
    }

    private static void insertOutbox(java.sql.Connection connection, UUID eventId, UUID aggregateId,
            String eventType, UUID causationId) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO flash_sale_outbox_events
                    (event_id, aggregate_type, aggregate_id, aggregate_version, event_type, event_version,
                     causation_id, payload, status, attempt_count, next_attempt_at,
                     published_at, created_at, updated_at)
                VALUES (?, 'PURCHASE_REQUEST', ?, 1, ?, 1, ?, '{}'::jsonb, 'PENDING', 0,
                        CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)) {
            statement.setObject(1, eventId);
            statement.setObject(2, aggregateId);
            statement.setString(3, eventType);
            statement.setObject(4, causationId);
            statement.executeUpdate();
        }
    }

    private static boolean columnExists(java.sql.Connection connection, String table, String column)
            throws Exception {
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (var result = statement.executeQuery()) {
                return result.next();
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
