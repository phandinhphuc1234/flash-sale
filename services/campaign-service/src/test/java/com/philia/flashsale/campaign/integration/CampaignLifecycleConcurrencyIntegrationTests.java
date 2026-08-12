package com.philia.flashsale.campaign.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.campaign.campaign.adapter.in.scheduling.CampaignLifecycleScheduler;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.CampaignLifecyclePersistenceAdapter;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.CampaignPersistenceAdapter;
import com.philia.flashsale.campaign.campaign.application.command.ActivateCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.command.EndCampaignCommand;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
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

/** PostgreSQL race coverage for one-winner Campaign activation and idempotent ending. */
@SpringBootTest
@Testcontainers
class CampaignLifecycleConcurrencyIntegrationTests {

    private static final Instant NOW = Instant.parse("2030-08-01T10:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("campaign_lifecycle_race_db")
            .withUsername("campaign")
            .withPassword("campaign");

    @Autowired
    private CampaignPersistenceAdapter campaignPersistence;

    @Autowired
    private CampaignLifecyclePersistenceAdapter lifecyclePersistence;

    @Autowired
    private JdbcTemplate jdbc;

    /** The real scheduled component is not part of a deterministic race test. */
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
    void concurrentActivationHasOneConditionalWinnerAndOneOutboxEvent() throws Exception {
        UUID campaignId = insertCampaign("SCHEDULED", 1, NOW.minusSeconds(1), NOW.plusSeconds(3600));
        Campaign first = load(campaignId);
        Campaign second = load(campaignId);
        first.markActive("worker-a", NOW);
        second.markActive("worker-b", NOW);

        List<Optional<Campaign>> results = race(
                () -> lifecyclePersistence.activate(first,
                        new ActivateCampaignCommand(campaignId, 1, "worker-a", NOW, "trace-a", false)),
                () -> lifecyclePersistence.activate(second,
                        new ActivateCampaignCommand(campaignId, 1, "worker-b", NOW, "trace-b", false)));

        assertThat(results.stream().filter(Optional::isPresent).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM campaigns WHERE id = ?", String.class, campaignId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT version FROM campaigns WHERE id = ?", Long.class, campaignId))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campaign_outbox_events "
                + "WHERE aggregate_id = ? AND event_type = 'CampaignActivated'", Integer.class, campaignId))
                .isEqualTo(1);
    }

    @Test
    void concurrentEndingHasOneConditionalWinnerAndDoesNotEmitEndedEvent() throws Exception {
        UUID campaignId = insertCampaign("ACTIVE", 2, NOW.minusSeconds(3600), NOW);
        Campaign first = load(campaignId);
        Campaign second = load(campaignId);
        first.markEnded("worker-a", NOW);
        second.markEnded("worker-b", NOW);

        List<Optional<Campaign>> results = race(
                () -> lifecyclePersistence.end(first,
                        new EndCampaignCommand(campaignId, 2, "worker-a", NOW, "trace-a")),
                () -> lifecyclePersistence.end(second,
                        new EndCampaignCommand(campaignId, 2, "worker-b", NOW, "trace-b")));

        assertThat(results.stream().filter(Optional::isPresent).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM campaigns WHERE id = ?", String.class, campaignId))
                .isEqualTo("ENDED");
        assertThat(jdbc.queryForObject("SELECT version FROM campaigns WHERE id = ?", Long.class, campaignId))
                .isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campaign_outbox_events "
                + "WHERE aggregate_id = ? AND event_type = 'CampaignEnded'", Integer.class, campaignId))
                .isZero();
    }

    private List<Optional<Campaign>> race(Supplier<Optional<Campaign>> first,
            Supplier<Optional<Campaign>> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Optional<Campaign>>> futures = new ArrayList<>();
            for (Supplier<Optional<Campaign>> operation : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    ready.await();
                    start.await();
                    return operation.get();
                }));
            }
            ready.await();
            start.countDown();
            return List.of(futures.get(0).get(), futures.get(1).get());
        } finally {
            executor.shutdownNow();
        }
    }

    private Campaign load(UUID campaignId) {
        return campaignPersistence.findById(campaignId).orElseThrow();
    }

    private UUID insertCampaign(String status, long version, Instant start, Instant end) {
        UUID campaignId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID allocationId = UUID.randomUUID();
        Instant createdAt = NOW.minusSeconds(3600);
        jdbc.update("""
                INSERT INTO campaigns (id, code, name, status, start_at, end_at, scheduled_at,
                    activated_at, version, created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, 'Lifecycle race', ?, ?, ?, ?, ?, ?, 'admin', 'admin', ?, ?)
                """, campaignId, ("LIFECYCLE-" + campaignId.toString().substring(0, 8)).toUpperCase(), status,
                Timestamp.from(start), Timestamp.from(end), Timestamp.from(start.minusSeconds(60)),
                "ACTIVE".equals(status) ? Timestamp.from(start) : null, version,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
        jdbc.update("""
                INSERT INTO campaign_items (id, campaign_id, product_id, variant_id,
                    inventory_allocation_id, variant_sku_snapshot, base_price_snapshot, currency_snapshot,
                    campaign_price, requested_quantity, allocated_quantity, purchase_limit_per_user,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'SKU-RACE', ?, 'VND', ?, 10, 10, 1, ?, ?)
                """, UUID.randomUUID(), campaignId, productId, variantId, allocationId,
                new BigDecimal("100.00"), new BigDecimal("90.00"),
                Timestamp.from(createdAt), Timestamp.from(createdAt));
        return campaignId;
    }
}
