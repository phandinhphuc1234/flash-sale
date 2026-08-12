package com.philia.flashsale.campaign.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL contract for one active schedule operation per Campaign version. */
@Testcontainers
class ScheduleCampaignConcurrencyIntegrationTests {

    private static final String CHANGELOG = "classpath:/db/changelog/db.changelog-master.yaml";
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
        liquibase.setChangeLog(CHANGELOG);
        liquibase.setShouldRun(true);
        liquibase.afterPropertiesSet();
    }

    @BeforeEach
    void clear() {
        jdbc.execute("TRUNCATE TABLE campaign_outbox_events, campaign_schedule_operations, campaign_items, campaigns");
    }

    @Test
    void concurrentSchedulePreparationLeavesOneActiveOperationAndOneWinner() throws Exception {
        UUID campaignId = insertCampaign();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> attempts = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            String key = "schedule-" + index;
            UUID inventoryRequestId = UUID.randomUUID();
            attempts.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    insertOperation(campaignId, key, inventoryRequestId, "STARTED");
                    return true;
                } catch (DataAccessException exception) {
                    return false;
                }
            }));
        }
        ready.await();
        start.countDown();
        int successes = 0;
        for (Future<Boolean> attempt : attempts) {
            if (attempt.get()) {
                successes++;
            }
        }
        executor.shutdownNow();

        assertThat(successes).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campaign_schedule_operations WHERE campaign_id = ? "
                + "AND operation_status IN ('STARTED', 'INVENTORY_ALLOCATED')", Integer.class, campaignId))
                .isEqualTo(1);
    }

    @Test
    void winnerCanFinalizeOneAllocationSnapshotAndOneScheduledOutboxRow() {
        UUID campaignId = insertCampaign();
        UUID inventoryRequestId = UUID.randomUUID();
        insertOperation(campaignId, "schedule-winner", inventoryRequestId, "STARTED");
        UUID allocationId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        jdbc.update("UPDATE campaign_schedule_operations SET operation_status = 'INVENTORY_ALLOCATED' "
                + "WHERE campaign_id = ?", campaignId);
        jdbc.update("""
                INSERT INTO campaign_items (
                    id, campaign_id, product_id, variant_id, inventory_allocation_id,
                    variant_sku_snapshot, base_price_snapshot, currency_snapshot,
                    campaign_price, requested_quantity, allocated_quantity,
                    purchase_limit_per_user, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'SKU-1', 120.0000, 'VND', 100.0000,
                          10, 10, 1, ?, ?)
                """, UUID.randomUUID(), campaignId, productId, variantId, allocationId, START, START);
        jdbc.update("UPDATE campaigns SET status = 'SCHEDULED', version = 1, scheduled_at = ?, updated_at = ? "
                + "WHERE id = ?", START, START, campaignId);
        jdbc.update("""
                INSERT INTO campaign_outbox_events (
                    id, aggregate_id, aggregate_version, event_type, event_version,
                    event_key, payload, publish_status, retry_count, occurred_at,
                    requeue_count, trace_id, created_at, updated_at
                ) VALUES (?, ?, 1, 'CampaignScheduled', 1, ?, CAST(? AS jsonb),
                          'PENDING', 0, ?, 0, 'trace', ?, ?)
                """, UUID.randomUUID(), campaignId, campaignId.toString(),
                "{\"allocationId\":\"" + allocationId + "\"}", START, START, START);

        assertThat(jdbc.queryForObject("SELECT status FROM campaigns WHERE id = ?", String.class, campaignId))
                .isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campaign_items WHERE campaign_id = ? "
                + "AND inventory_allocation_id = ?", Integer.class, campaignId, allocationId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campaign_outbox_events WHERE aggregate_id = ? "
                + "AND event_type = 'CampaignScheduled'", Integer.class, campaignId)).isEqualTo(1);
    }

    private static UUID insertCampaign() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO campaigns (id, code, name, status, start_at, end_at, version,
                    created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, 'Campaign', 'DRAFT', ?, ?, 0, 'admin', 'admin', ?, ?)
                """, id, "CAMPAIGN_" + id.toString().substring(0, 8).toUpperCase(), START, END, START, START);
        return id;
    }

    private static void insertOperation(UUID campaignId, String key, UUID inventoryRequestId, String status) {
        jdbc.update("""
                INSERT INTO campaign_schedule_operations (
                    id, campaign_id, idempotency_key, inventory_request_id, request_hash,
                    campaign_version, operation_status, attempt_count, initiated_by,
                    trace_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 0, ?, 0, 'admin', 'trace', ?, ?)
                """, UUID.randomUUID(), campaignId, key, inventoryRequestId, "a".repeat(64),
                status, START, START);
    }
}
