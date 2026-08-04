package com.philia.flashsale.campaign.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.campaign.campaign.adapter.in.scheduling.CampaignLifecycleScheduler;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.CampaignLifecyclePersistenceAdapter;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.CampaignPersistenceAdapter;
import com.philia.flashsale.campaign.campaign.application.command.ActivateCampaignCommand;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies scheduled-before-activated ordering and the deliberate absence of ended events. */
@SpringBootTest
@Testcontainers
class CampaignLifecycleOutboxOrderingTests {

    private static final Instant NOW = Instant.parse("2030-08-01T10:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("campaign_lifecycle_outbox_db")
            .withUsername("campaign")
            .withPassword("campaign");

    @Autowired
    private CampaignPersistenceAdapter campaignPersistence;

    @Autowired
    private CampaignLifecyclePersistenceAdapter lifecyclePersistence;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private CampaignLifecycleScheduler scheduler;

    @MockBean
    private ClientRegistrationRepository clientRegistrations;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @BeforeEach
    void clearBusinessData() {
        jdbc.execute("TRUNCATE TABLE campaign_outbox_events, campaign_schedule_operations, "
                + "campaign_items, campaigns");
    }

    @Test
    void activationCommitsStateAndActivatedOutboxAfterExistingScheduledEvent() {
        UUID campaignId = insertScheduledCampaign();
        jdbc.update("""
                INSERT INTO campaign_outbox_events (
                    id, aggregate_id, aggregate_version, event_type, event_version,
                    event_key, payload, publish_status, retry_count, occurred_at,
                    requeue_count, trace_id, created_at, updated_at)
                VALUES (?, ?, 1, 'CampaignScheduled', 1, ?, CAST(? AS jsonb),
                          'PENDING', 0, ?, 0, 'schedule-trace', ?, ?)
                """, UUID.randomUUID(), campaignId, campaignId.toString(),
                "{\"eventType\":\"CampaignScheduled\"}", Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW.minusSeconds(60)));

        Campaign candidate = campaignPersistence.findById(campaignId).orElseThrow();
        candidate.markActive("admin", NOW);
        assertThat(lifecyclePersistence.activate(candidate,
                new ActivateCampaignCommand(campaignId, 1, "admin", NOW, "activation-trace", true)))
                .isPresent();

        List<String> eventTypes = jdbc.queryForList(
                "SELECT event_type FROM campaign_outbox_events WHERE aggregate_id = ? "
                        + "ORDER BY aggregate_version, id", String.class, campaignId);
        assertThat(eventTypes).containsExactly("CampaignScheduled", "CampaignActivated");
        assertThat(jdbc.queryForObject("SELECT status FROM campaigns WHERE id = ?", String.class, campaignId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT aggregate_version FROM campaign_outbox_events "
                + "WHERE aggregate_id = ? AND event_type = 'CampaignActivated'", Long.class, campaignId))
                .isEqualTo(2L);
    }

    @Test
    void endingDoesNotCreateCampaignEndedOutboxBeforeKafkaContractExists() {
        UUID campaignId = insertActiveCampaign();
        Campaign candidate = campaignPersistence.findById(campaignId).orElseThrow();
        candidate.markEnded("scheduler", NOW);

        assertThat(lifecyclePersistence.end(candidate,
                new com.philia.flashsale.campaign.campaign.application.command.EndCampaignCommand(
                        campaignId, 2, "scheduler", NOW, "ending-trace"))).isPresent();
        assertThat(jdbc.queryForObject("SELECT status FROM campaigns WHERE id = ?", String.class, campaignId))
                .isEqualTo("ENDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campaign_outbox_events "
                + "WHERE aggregate_id = ? AND event_type = 'CampaignEnded'", Integer.class, campaignId))
                .isZero();
    }

    private UUID insertScheduledCampaign() {
        return insertCampaign("SCHEDULED", 1, NOW.minusSeconds(1), NOW.plusSeconds(3600));
    }

    private UUID insertActiveCampaign() {
        return insertCampaign("ACTIVE", 2, NOW.minusSeconds(3600), NOW);
    }

    private UUID insertCampaign(String status, long version, Instant start, Instant end) {
        UUID campaignId = UUID.randomUUID();
        Instant createdAt = NOW.minusSeconds(3600);
        jdbc.update("""
                INSERT INTO campaigns (id, code, name, status, start_at, end_at, scheduled_at,
                    activated_at, version, created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, 'Outbox lifecycle', ?, ?, ?, ?, ?, ?, 'admin', 'admin', ?, ?)
                """, campaignId, ("OUTBOX-" + campaignId.toString().substring(0, 8)).toUpperCase(), status,
                Timestamp.from(start), Timestamp.from(end), Timestamp.from(start.minusSeconds(60)),
                "ACTIVE".equals(status) ? Timestamp.from(start) : null, version,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
        jdbc.update("""
                INSERT INTO campaign_items (id, campaign_id, product_id, variant_id,
                    inventory_allocation_id, variant_sku_snapshot, base_price_snapshot, currency_snapshot,
                    campaign_price, requested_quantity, allocated_quantity, purchase_limit_per_user,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'SKU-OUTBOX', ?, 'VND', ?, 10, 10, 1, ?, ?)
                """, UUID.randomUUID(), campaignId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.00"), new BigDecimal("90.00"),
                Timestamp.from(createdAt), Timestamp.from(createdAt));
        return campaignId;
    }
}
