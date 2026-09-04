package com.philia.flashsale.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Checks that regular checkout expands the Order schema without removing legacy Flash Sale fields. */
@Testcontainers(disabledWithoutDocker = true)
class RegularPurchaseMigrationIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("order_regular_purchase_db")
            .withUsername("order")
            .withPassword("order");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateDatabase() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:/db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
    }

    @Test
    void keepsLegacyIdentifiersAndAddsRegularPurchaseIntakeBoundary() {
        assertThat(tableExists("regular_purchase_requests")).isTrue();
        assertThat(columnExists("orders", "reservation_id")).isTrue();
        assertThat(columnExists("orders", "campaign_id")).isTrue();
        assertThat(columnExists("orders", "purchase_source")).isTrue();
        assertThat(columnExists("orders", "stock_participant_type")).isTrue();
        assertThat(columnExists("orders", "stock_reference_id")).isTrue();
        assertThat(columnExists("purchase_sagas", "stock_reference_id")).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from databasechangelog where id = '003-add-regular-purchase-checkout'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from databasechangelog where id = '004-allow-cart-intake-before-snapshot'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from databasechangelog where id = '005-allow-regular-order-event-versions'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void allowsCartIdempotencyIntakeBeforeSnapshotButRequiresCartIdentityAfterward() {
        insertCartRequest("RECEIVED", null, null);

        assertThatThrownBy(() -> insertCartRequest("SNAPSHOT_VALIDATED", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertCartRequest("SNAPSHOT_VALIDATED", UUID.randomUUID(), 0L);
    }

    @Test
    void keepsLegacyOutboxRowsAtV1AndAllowsOnlyDocumentedRegularV2Facts() {
        UUID orderId = UUID.randomUUID();
        insertOutbox(orderId, "OrderCreatedV2", 2);
        assertThatThrownBy(() -> insertOutbox(UUID.randomUUID(), "OrderCreatedV2", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertOutbox(UUID.randomUUID(), "OrderCreated", 2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static void insertCartRequest(String state, UUID cartId, Long cartVersion) {
        OffsetDateTime now = OffsetDateTime.parse("2030-01-01T10:00:00Z");
        jdbc.update("""
                insert into regular_purchase_requests (
                    id, shopper_id, idempotency_key, request_fingerprint, source, state,
                    proposed_order_id, proposed_hold_id, cart_id, cart_version, snapshot_payload,
                    created_at, updated_at
                ) values (?, ?, ?, ?, 'CART', ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """,
                UUID.randomUUID(), UUID.randomUUID(), "key-" + UUID.randomUUID(), "a".repeat(64), state,
                UUID.randomUUID(), UUID.randomUUID(), cartId, cartVersion, "{\"items\":[]}", now, now);
    }

    private static void insertOutbox(UUID eventId, String eventType, int eventVersion) {
        OffsetDateTime now = OffsetDateTime.parse("2030-01-01T10:00:00Z");
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                insert into order_outbox_events (
                    event_id, aggregate_type, aggregate_id, aggregate_version, event_type, event_version,
                    event_key, correlation_id, causation_id, payload, status, attempt_count, next_attempt_at,
                    occurred_at, created_at, updated_at
                ) values (?, 'ORDER', ?, 1, ?, ?, ?, ?, ?, ?::jsonb, 'PENDING', 0, ?, ?, ?, ?)
                """,
                eventId, orderId, eventType, eventVersion, orderId.toString(), UUID.randomUUID(), UUID.randomUUID(),
                "{}", now, now, now, now);
    }

    private static boolean tableExists(String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from information_schema.tables
                    where table_schema = 'public' and table_name = ?
                )
                """, Boolean.class, table));
    }

    private static boolean columnExists(String table, String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from information_schema.columns
                    where table_schema = 'public' and table_name = ? and column_name = ?
                )
                """, Boolean.class, table, column));
    }
}
