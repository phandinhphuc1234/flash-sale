package com.philia.flashsale.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies the Order-owned Liquibase schema and its database-level invariants. */
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
class OrderSchemaMigrationIntegrationTests {

    private static final String CHANGELOG = "classpath:/db/changelog/db.changelog-master.yaml";
    private static final OffsetDateTime ACCEPTED = OffsetDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime EXPIRES = ACCEPTED.plusHours(2);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("order_db")
            .withUsername("order")
            .withPassword("order");

    private static JdbcTemplate jdbc;
    private static DataSource dataSource;

    @org.junit.jupiter.api.BeforeAll
    static void migrateDatabase() throws Exception {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(CHANGELOG);
        liquibase.setShouldRun(true);
        liquibase.afterPropertiesSet();
    }

    @Test
    @Order(1)
    void migrationCreatesOnlyOrderTablesAndLiquibaseLedger() {
        assertThat(publicTables()).containsExactlyInAnyOrder(
                "orders", "order_lines", "order_consumer_inbox", "order_outbox_events",
                "databasechangelog", "databasechangeloglock");
        assertThat(jdbc.queryForObject("SELECT locked FROM databasechangeloglock WHERE id = 1", Boolean.class))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class)).isEqualTo(1);
    }

    @Test
    @Order(2)
    void migrationUsesExactMoneyAndTimestampTypesAndRequiredIndexes() {
        assertNumeric("orders", "subtotal_amount");
        assertNumeric("orders", "total_amount");
        assertNumeric("order_lines", "unit_price");
        assertNumeric("order_lines", "line_amount");
        assertThat(columnType("orders", "accepted_at")).isEqualTo("timestamp with time zone");
        assertThat(columnType("orders", "reservation_expires_at")).isEqualTo("timestamp with time zone");
        assertThat(columnType("order_outbox_events", "payload")).isEqualTo("jsonb");

        assertThat(indexes()).contains(
                "idx_orders_owner_created",
                "idx_order_consumer_inbox_purchase_request",
                "idx_order_consumer_inbox_reservation",
                "idx_order_outbox_events_due",
                "idx_order_outbox_events_claim_recovery");
    }

    @Test
    @Order(3)
    void orderConstraintsProtectIdentityStatusMoneyAndTemporalWindow() {
        UUID orderId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String orderNumber = "ORD-" + orderId;
        insertOrder(orderId, orderNumber, purchaseRequestId, reservationId,
                "PENDING_PAYMENT", "VND", new BigDecimal("10.0000"), new BigDecimal("10.0000"));

        assertThatThrownBy(() -> insertOrder(UUID.randomUUID(), orderNumber,
                UUID.randomUUID(), UUID.randomUUID(), "PENDING_PAYMENT", "VND",
                new BigDecimal("1.0000"), new BigDecimal("1.0000")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertOrder(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                purchaseRequestId, UUID.randomUUID(), "PENDING_PAYMENT", "VND",
                new BigDecimal("1.0000"), new BigDecimal("1.0000")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertOrder(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                UUID.randomUUID(), reservationId, "PENDING_PAYMENT", "VND",
                new BigDecimal("1.0000"), new BigDecimal("1.0000")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertOrder(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "CONFIRMED", "VND",
                new BigDecimal("1.0000"), new BigDecimal("1.0000")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertOrder(UUID.randomUUID(), " ",
                UUID.randomUUID(), UUID.randomUUID(), "PENDING_PAYMENT", "VND",
                new BigDecimal("1.0000"), new BigDecimal("1.0000")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertOrder(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "PENDING_PAYMENT", "vnd",
                new BigDecimal("1.0000"), new BigDecimal("1.0000")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertOrder(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "PENDING_PAYMENT", "VND",
                new BigDecimal("1.0000"), new BigDecimal("2.0000")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertOrderWithWindow(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                ACCEPTED, ACCEPTED))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @Order(4)
    void lineAndInboxConstraintsProtectForeignKeysAndDeduplicationEvidence() {
        UUID orderId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        insertOrder(orderId, "ORD-" + orderId, purchaseRequestId, reservationId,
                "PENDING_PAYMENT", "USD", new BigDecimal("20.0000"), new BigDecimal("20.0000"));
        UUID variantId = UUID.randomUUID();
        insertLine(orderId, variantId, 2, new BigDecimal("10.0000"), new BigDecimal("20.0000"));

        assertThatThrownBy(() -> insertLine(orderId, variantId, 1, new BigDecimal("1.0000"), new BigDecimal("1.0000")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertLine(UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal("1.0000"), new BigDecimal("1.0000")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertLine(orderId, UUID.randomUUID(), 0,
                new BigDecimal("1.0000"), new BigDecimal("1.0000")))
                .isInstanceOf(DataAccessException.class);

        UUID eventId = UUID.randomUUID();
        insertInbox(eventId, orderId, purchaseRequestId, reservationId, purchaseRequestId, 0, 10);
        assertThatThrownBy(() -> insertInbox(UUID.randomUUID(), orderId, purchaseRequestId, reservationId,
                purchaseRequestId, 0, 10)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertInbox(UUID.randomUUID(), orderId, UUID.randomUUID(), reservationId,
                purchaseRequestId, 0, 11)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertInbox(UUID.randomUUID(), orderId, purchaseRequestId, reservationId,
                UUID.randomUUID(), 0, 12)).isInstanceOf(DataAccessException.class);
    }

    @Test
    @Order(5)
    void outboxConstraintsProtectStablePublicationIdentityAndRetryState() {
        UUID orderId = UUID.randomUUID();
        insertOrder(orderId, "ORD-" + orderId, UUID.randomUUID(), UUID.randomUUID(),
                "PENDING_PAYMENT", "VND", new BigDecimal("2.0000"), new BigDecimal("2.0000"));
        UUID eventId = UUID.randomUUID();
        insertOutbox(eventId, orderId, "PENDING", 0);

        assertThatThrownBy(() -> insertOutbox(UUID.randomUUID(), orderId, "PENDING", 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertOutbox(UUID.randomUUID(), UUID.randomUUID(), "BROKEN", 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertOutbox(UUID.randomUUID(), UUID.randomUUID(), "PENDING", -1))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @Order(99)
    void developmentRollbackRemovesOrderObjectsInReverseDependencyOrder() throws Exception {
        try (var connection = dataSource.getConnection()) {
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(), database);
            liquibase.rollback(1, new Contexts(), new LabelExpression());
        }

        assertThat(publicTables()).containsExactlyInAnyOrder("databasechangelog", "databasechangeloglock");
    }

    private static void insertOrder(
            UUID orderId, String orderNumber, UUID purchaseRequestId, UUID reservationId,
            String status, String currency, BigDecimal subtotal, BigDecimal total) {
        insertOrderWithWindow(orderId, purchaseRequestId, reservationId, ACCEPTED, EXPIRES,
                orderNumber, status, currency, subtotal, total);
    }

    private static void insertOrderWithWindow(UUID orderId, UUID purchaseRequestId, UUID reservationId,
            OffsetDateTime acceptedAt, OffsetDateTime expiresAt) {
        insertOrderWithWindow(orderId, purchaseRequestId, reservationId, acceptedAt, expiresAt,
                "ORD-" + orderId, "PENDING_PAYMENT", "VND",
                new BigDecimal("1.0000"), new BigDecimal("1.0000"));
    }

    private static void insertOrderWithWindow(UUID orderId, UUID purchaseRequestId, UUID reservationId,
            OffsetDateTime acceptedAt, OffsetDateTime expiresAt, String orderNumber, String status,
            String currency, BigDecimal subtotal, BigDecimal total) {
        jdbc.update("""
                INSERT INTO orders (
                    id, order_number, purchase_request_id, reservation_id, campaign_id, user_id,
                    status, currency, subtotal_amount, total_amount, accepted_at,
                    reservation_expires_at, row_version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, orderId, orderNumber, purchaseRequestId, reservationId, UUID.randomUUID(), UUID.randomUUID(),
                status, currency, subtotal, total, acceptedAt, expiresAt, ACCEPTED, ACCEPTED);
    }

    private static void insertLine(UUID orderId, UUID variantId, long quantity, BigDecimal unitPrice,
            BigDecimal lineAmount) {
        jdbc.update("""
                INSERT INTO order_lines (id, order_id, variant_id, quantity, unit_price, line_amount, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), orderId, variantId, quantity, unitPrice, lineAmount, ACCEPTED);
    }

    private static void insertInbox(UUID eventId, UUID orderId, UUID purchaseRequestId, UUID reservationId,
            UUID aggregateId, int partition, long offset) {
        jdbc.update("""
                INSERT INTO order_consumer_inbox (
                    event_id, event_type, event_version, producer, aggregate_id, aggregate_version,
                    purchase_request_id, reservation_id, payload_fingerprint, source_topic,
                    source_partition, source_offset, order_id, processed_at
                ) VALUES (?, 'PurchaseAccepted', 1, 'flashsale-service', ?, 1, ?, ?, ?,
                          'flashsale.purchase.events.v1', ?, ?, ?, ?)
                """, eventId, aggregateId, purchaseRequestId, reservationId, "a".repeat(64),
                partition, offset, orderId, ACCEPTED);
    }

    private static void insertOutbox(UUID eventId, UUID orderId, String status, int attemptCount) {
        jdbc.update("""
                INSERT INTO order_outbox_events (
                    event_id, aggregate_type, aggregate_id, aggregate_version, event_type, event_version,
                    event_key, correlation_id, causation_id, payload, status, attempt_count,
                    next_attempt_at, occurred_at, created_at, updated_at
                ) VALUES (?, 'ORDER', ?, 1, 'OrderCreated', 1, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?)
                """, eventId, orderId, orderId.toString(), UUID.randomUUID(), UUID.randomUUID(), "{}", status,
                attemptCount, ACCEPTED, ACCEPTED, ACCEPTED, ACCEPTED);
    }

    private static void assertNumeric(String table, String column) {
        Map<String, Object> metadata = jdbc.queryForMap("""
                SELECT data_type, numeric_precision, numeric_scale
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, table, column);
        assertThat(metadata.get("data_type")).isEqualTo("numeric");
        assertThat(((Number) metadata.get("numeric_precision")).intValue()).isEqualTo(19);
        assertThat(((Number) metadata.get("numeric_scale")).intValue()).isEqualTo(4);
    }

    private static String columnType(String table, String column) {
        return jdbc.queryForObject("""
                SELECT CASE WHEN data_type = 'USER-DEFINED' THEN udt_name ELSE data_type END
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }

    private static List<String> publicTables() {
        return jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """, String.class);
    }

    private static List<String> indexes() {
        return jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'public'
                ORDER BY indexname
                """, String.class);
    }
}
