package com.philia.flashsale.campaign.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.sql.DataSource;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the Campaign-owned Liquibase schema and its database-level invariants.
 *
 * <p>The migration is intentionally added by T008. These tests are the red-first
 * contract for the four tables and must remain independent of Campaign domain code.</p>
 */
@Testcontainers
class CampaignSchemaMigrationIntegrationTests {

    private static final String CHANGELOG = "classpath:/db/changelog/db.changelog-master.yaml";
    private static final OffsetDateTime START = OffsetDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime END = START.plusHours(2);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("campaign_db")
            .withUsername("campaign")
            .withPassword("campaign");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateDatabase() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        newLiquibase(dataSource).afterPropertiesSet();
    }

    @Test
    void migrationCreatesCampaignTablesAndLiquibaseLedger() {
        assertThat(publicTables()).containsExactlyInAnyOrder(
                "campaigns",
                "campaign_items",
                "campaign_schedule_operations",
                "campaign_outbox_events",
                "databasechangelog",
                "databasechangeloglock");

        assertThat(jdbc.queryForObject("SELECT locked FROM databasechangeloglock WHERE id = 1", Boolean.class))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void campaignConstraintsProtectCodeStatusAndTimeWindow() {
        UUID campaignId = UUID.randomUUID();
        insertCampaign(campaignId, "CAMPAIGN_" + campaignId.toString().substring(0, 8).toUpperCase());

        assertThatThrownBy(() -> insertCampaign(
                UUID.randomUUID(),
                "campaign_" + campaignId.toString().substring(0, 8)))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO campaigns (
                    id, code, name, status, start_at, end_at, version,
                    created_by, updated_by, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                "INVALID_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "Invalid status",
                "BROKEN",
                START,
                END,
                "test",
                "test",
                START,
                START))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO campaigns (
                    id, code, name, status, start_at, end_at, version,
                    created_by, updated_by, created_at, updated_at
                ) VALUES (?, ?, ?, 'DRAFT', ?, ?, 0, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                "WINDOW_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "Invalid window",
                END,
                START,
                "test",
                "test",
                START,
                START))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void campaignItemConstraintsProtectOneItemAndPositiveQuantities() {
        UUID campaignId = UUID.randomUUID();
        insertCampaign(campaignId, "ITEM_" + campaignId.toString().substring(0, 8).toUpperCase());
        insertItem(campaignId, UUID.randomUUID(), 100, 0, 1, "VND");

        assertThatThrownBy(() -> insertItem(campaignId, UUID.randomUUID(), 100, 0, 1, "VND"))
                .isInstanceOf(DataAccessException.class);

        UUID invalidCampaign = UUID.randomUUID();
        insertCampaign(invalidCampaign, "BAD_ITEM_" + invalidCampaign.toString().substring(0, 8).toUpperCase());
        assertThatThrownBy(() -> insertItem(invalidCampaign, UUID.randomUUID(), 0, -1, 0, "USD"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void scheduleOperationConstraintsProtectIdempotencyAndActiveOperationUniqueness() {
        UUID campaignId = UUID.randomUUID();
        insertCampaign(campaignId, "OP_" + campaignId.toString().substring(0, 8).toUpperCase());
        String requestHash = "a".repeat(64);
        UUID firstInventoryRequest = UUID.randomUUID();
        insertScheduleOperation(campaignId, "schedule-1", firstInventoryRequest, requestHash, "STARTED", 0);

        assertThatThrownBy(() -> insertScheduleOperation(
                campaignId, "schedule-1", UUID.randomUUID(), requestHash, "FAILED", 1))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertScheduleOperation(
                campaignId, "schedule-2", firstInventoryRequest, requestHash, "FAILED", 1))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertScheduleOperation(
                campaignId, "schedule-3", UUID.randomUUID(), requestHash, "STARTED", -1))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void outboxConstraintsProtectAggregateOrderingAndRetryState() {
        UUID aggregateId = UUID.randomUUID();
        insertCampaign(aggregateId, "OUTBOX_" + aggregateId.toString().substring(0, 8).toUpperCase());
        insertOutboxEvent(aggregateId, 1, "CampaignScheduled", 1, "PENDING");

        assertThatThrownBy(() -> insertOutboxEvent(aggregateId, 1, "CampaignScheduled", 0, "PENDING"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertOutboxEvent(UUID.randomUUID(), 1, "UnknownEvent", -1, "BROKEN"))
                .isInstanceOf(DataAccessException.class);
    }

    private static SpringLiquibase newLiquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(CHANGELOG);
        liquibase.setShouldRun(true);
        return liquibase;
    }

    private static void insertCampaign(UUID id, String code) {
        jdbc.update("""
                INSERT INTO campaigns (
                    id, code, name, status, start_at, end_at, version,
                    created_by, updated_by, created_at, updated_at
                ) VALUES (?, ?, 'Campaign', 'DRAFT', ?, ?, 0, 'test', 'test', ?, ?)
                """, id, code, START, END, START, START);
    }

    private static void insertItem(
            UUID campaignId,
            UUID variantId,
            long campaignPrice,
            long allocatedQuantity,
            long purchaseLimit,
            String currency) {
        jdbc.update("""
                INSERT INTO campaign_items (
                    id, campaign_id, variant_id, campaign_price, requested_quantity,
                    allocated_quantity, purchase_limit_per_user, currency_snapshot,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, 100, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), campaignId, variantId, campaignPrice,
                allocatedQuantity, purchaseLimit, currency, START, START);
    }

    private static void insertScheduleOperation(
            UUID campaignId,
            String idempotencyKey,
            UUID inventoryRequestId,
            String requestHash,
            String status,
            int attemptCount) {
        jdbc.update("""
                INSERT INTO campaign_schedule_operations (
                    id, campaign_id, idempotency_key, inventory_request_id, request_hash,
                    campaign_version, operation_status, attempt_count, initiated_by,
                    trace_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 0, ?, ?, 'test', 'trace', ?, ?)
                """, UUID.randomUUID(), campaignId, idempotencyKey, inventoryRequestId,
                requestHash, status, attemptCount, START, START);
    }

    private static void insertOutboxEvent(
            UUID aggregateId,
            long aggregateVersion,
            String eventType,
            int eventVersion,
            String publishStatus) {
        jdbc.update("""
                INSERT INTO campaign_outbox_events (
                    id, aggregate_id, aggregate_version, event_type, event_version,
                    event_key, payload, publish_status, retry_count, occurred_at,
                    requeue_count, trace_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 0, ?, 0, 'trace', ?, ?)
                """, UUID.randomUUID(), aggregateId, aggregateVersion, eventType, eventVersion,
                aggregateId.toString(), "{}", publishStatus, START, START, START);
    }

    private static java.util.List<String> publicTables() {
        return jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """, String.class);
    }
}
