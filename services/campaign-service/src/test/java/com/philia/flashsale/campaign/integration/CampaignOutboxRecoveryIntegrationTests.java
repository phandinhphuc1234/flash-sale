package com.philia.flashsale.campaign.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.campaign.outbox.adapter.out.persistence.jpa.OutboxPersistenceAdapter;
import com.philia.flashsale.campaign.outbox.application.model.OutboxClaim;
import com.philia.flashsale.campaign.outbox.application.model.OutboxPublishStatus;
import com.philia.flashsale.campaign.outbox.application.model.OutboxRequeueResult;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL proof for claim/lease, ordering, retry, terminal failure, and requeue semantics. */
@Testcontainers
@DataJpaTest(properties = {"spring.liquibase.enabled=false", "spring.jpa.hibernate.ddl-auto=none"})
@Import(OutboxPersistenceAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CampaignOutboxRecoveryIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OutboxPersistenceAdapter adapter;

    @BeforeEach
    void createSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS campaign_outbox_events (
                  id UUID PRIMARY KEY,
                  aggregate_id UUID NOT NULL,
                  aggregate_version BIGINT NOT NULL,
                  event_type VARCHAR(100) NOT NULL,
                  event_version INTEGER NOT NULL,
                  event_key VARCHAR(100) NOT NULL,
                  payload JSONB NOT NULL,
                  publish_status VARCHAR(32) NOT NULL,
                  retry_count INTEGER NOT NULL DEFAULT 0,
                  next_attempt_at TIMESTAMPTZ,
                  claimed_by VARCHAR(100),
                  claimed_until TIMESTAMPTZ,
                  occurred_at TIMESTAMPTZ NOT NULL,
                  published_at TIMESTAMPTZ,
                  last_error VARCHAR(2000),
                  requeue_count INTEGER NOT NULL DEFAULT 0,
                  requeued_by VARCHAR(100),
                  requeued_at TIMESTAMPTZ,
                  trace_id VARCHAR(128) NOT NULL,
                  created_at TIMESTAMPTZ NOT NULL,
                  updated_at TIMESTAMPTZ NOT NULL
                )
                """);
        jdbc.execute("TRUNCATE TABLE campaign_outbox_events");
    }

    @AfterEach
    void cleanSchema() {
        jdbc.execute("TRUNCATE TABLE campaign_outbox_events");
    }

    @Test
    void onlyOneWorkerClaimsTheSameDueRow() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID eventId = seed(campaignId, 1, "PENDING", Instant.now());
        Instant now = Instant.now();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        var first = executor.submit(() -> claimAfter(start, ready, "worker-a", now));
        var second = executor.submit(() -> claimAfter(start, ready, "worker-b", now));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<OutboxClaim> claims = new ArrayList<>();
        claims.addAll(first.get(5, TimeUnit.SECONDS));
        claims.addAll(second.get(5, TimeUnit.SECONDS));
        executor.shutdownNow();
        assertThat(claims).extracting(OutboxClaim::id).containsExactly(eventId);
    }

    @Test
    void expiredLeaseCanBeReclaimedAndEarlierAggregateVersionBlocksLaterEvent() {
        UUID campaignId = UUID.randomUUID();
        UUID first = seed(campaignId, 1, "PROCESSING", Instant.now().minusSeconds(30));
        jdbc.update("UPDATE campaign_outbox_events SET claimed_by=?, claimed_until=? WHERE id=?",
                "dead-worker", Timestamp.from(Instant.now().minusSeconds(1)), first);
        UUID second = seed(campaignId, 2, "PENDING", Instant.now());

        List<OutboxClaim> reclaimed = adapter.claimDue("worker", Instant.now(), Duration.ofSeconds(30), 10);
        assertThat(reclaimed).extracting(OutboxClaim::id).containsExactly(first);
        assertThat(adapter.claimDue("worker-2", Instant.now(), Duration.ofSeconds(30), 10)).isEmpty();
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void tenthFailureIsTerminalAndRequeueKeepsIdentityAndAuditsOperator() {
        UUID campaignId = UUID.randomUUID();
        UUID eventId = seed(campaignId, 1, "PENDING", Instant.now());
        Instant now = Instant.now();
        for (int attempt = 1; attempt <= 10; attempt++) {
            List<OutboxClaim> claims = adapter.claimDue("worker", now, Duration.ofSeconds(30), 1);
            assertThat(claims).hasSize(1);
            adapter.recordFailure(eventId, "worker", now, "Bearer secret=do-not-store", 10,
                    Duration.ofSeconds(60));
            if (attempt == 1) {
                Timestamp nextAttempt = jdbc.queryForObject(
                        "SELECT next_attempt_at FROM campaign_outbox_events WHERE id=?",
                        Timestamp.class, eventId);
                assertThat(Duration.between(now.plusSeconds(2), nextAttempt.toInstant()).abs())
                        .isLessThan(Duration.ofMillis(1));
            }
            now = now.plusSeconds(120);
        }
        OutboxRequeueResult result = adapter.requeue(campaignId, eventId, "operator-1", now);
        assertThat(result.publishStatus()).isEqualTo(OutboxPublishStatus.PENDING);
        assertThat(result.retryCount()).isZero();
        assertThat(result.requeueCount()).isEqualTo(1);
        assertThat(result.eventId()).isEqualTo(eventId);
        String error = jdbc.queryForObject("SELECT last_error FROM campaign_outbox_events WHERE id=?",
                String.class, eventId);
        assertThat(error).doesNotContain("do-not-store");
    }

    private List<OutboxClaim> claimAfter(CountDownLatch start, CountDownLatch ready,
            String worker, Instant now) throws InterruptedException {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        return adapter.claimDue(worker, now, Duration.ofSeconds(30), 1);
    }

    private UUID seed(UUID campaignId, long version, String status, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO campaign_outbox_events
                (id, aggregate_id, aggregate_version, event_type, event_version, event_key, payload,
                 publish_status, retry_count, next_attempt_at, occurred_at, trace_id, created_at, updated_at)
                VALUES (?, ?, ?, 'CampaignScheduled', 1, ?, '{}'::jsonb, ?, 0, ?, ?, 'trace-test', ?, ?)
                """, id, campaignId, version, campaignId.toString(), status,
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        return id;
    }
}
