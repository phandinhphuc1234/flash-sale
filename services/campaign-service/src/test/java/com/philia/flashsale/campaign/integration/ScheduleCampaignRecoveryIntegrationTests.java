package com.philia.flashsale.campaign.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Persistence contract for crash-window recovery and stable Inventory request identity. */
@Testcontainers
class ScheduleCampaignRecoveryIntegrationTests {

    private static final OffsetDateTime START = OffsetDateTime.of(2030, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime END = START.plusHours(1);
    private static JdbcTemplate jdbc;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("campaign_db")
            .withUsername("campaign")
            .withPassword("campaign");

    @BeforeAll
    static void migrate() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:/db/changelog/db.changelog-master.yaml");
        liquibase.setShouldRun(true);
        liquibase.afterPropertiesSet();
    }

    @BeforeEach
    void clear() {
        jdbc.execute("TRUNCATE TABLE campaign_outbox_events, campaign_schedule_operations, campaign_items, campaigns");
    }

    @Test
    void recoveryReusesTheOriginalOperationAndInventoryRequestIdentity() {
        UUID campaignId = insertCampaign();
        UUID operationId = UUID.randomUUID();
        UUID inventoryRequestId = UUID.randomUUID();
        insertOperation(operationId, campaignId, "schedule-recovery", inventoryRequestId, "STARTED");

        jdbc.update("UPDATE campaign_schedule_operations SET operation_status = 'INVENTORY_ALLOCATED', "
                + "attempt_count = attempt_count + 1 WHERE id = ?", operationId);
        Map<String, Object> row = jdbc.queryForMap("SELECT id, inventory_request_id, operation_status "
                + "FROM campaign_schedule_operations WHERE campaign_id = ? AND idempotency_key = ?",
                campaignId, "schedule-recovery");

        assertThat(row.get("id")).isEqualTo(operationId);
        assertThat(row.get("inventory_request_id")).isEqualTo(inventoryRequestId);
        assertThat(row.get("operation_status")).isEqualTo("INVENTORY_ALLOCATED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campaign_schedule_operations WHERE campaign_id = ?",
                Integer.class, campaignId)).isEqualTo(1);
    }

    @Test
    void ambiguousTimeoutDoesNotCreateAReplacementOperationOrRequestId() {
        UUID campaignId = insertCampaign();
        UUID inventoryRequestId = UUID.randomUUID();
        insertOperation(UUID.randomUUID(), campaignId, "schedule-timeout", inventoryRequestId, "STARTED");

        Map<String, Object> firstLookup = jdbc.queryForMap("SELECT inventory_request_id, request_hash "
                + "FROM campaign_schedule_operations WHERE campaign_id = ? AND idempotency_key = ?",
                campaignId, "schedule-timeout");
        Map<String, Object> retryLookup = jdbc.queryForMap("SELECT inventory_request_id, request_hash "
                + "FROM campaign_schedule_operations WHERE campaign_id = ? AND idempotency_key = ?",
                campaignId, "schedule-timeout");

        assertThat(retryLookup).isEqualTo(firstLookup);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campaign_schedule_operations "
                + "WHERE inventory_request_id = ?", Integer.class, inventoryRequestId)).isEqualTo(1);
    }

    private static UUID insertCampaign() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO campaigns (id, code, name, status, start_at, end_at, version,
                    created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, 'Campaign', 'DRAFT', ?, ?, 0, 'admin', 'admin', ?, ?)
                """, id, "RECOVERY_" + id.toString().substring(0, 8).toUpperCase(), START, END, START, START);
        return id;
    }

    private static void insertOperation(
            UUID operationId, UUID campaignId, String key, UUID inventoryRequestId, String status) {
        jdbc.update("""
                INSERT INTO campaign_schedule_operations (
                    id, campaign_id, idempotency_key, inventory_request_id, request_hash,
                    campaign_version, operation_status, attempt_count, initiated_by,
                    trace_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 0, ?, 0, 'admin', 'trace', ?, ?)
                """, operationId, campaignId, key, inventoryRequestId, "b".repeat(64), status, START, START);
    }
}
