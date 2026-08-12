package com.philia.flashsale.flashsale.outbox.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.flashsale.outbox.adapter.out.persistence.jpa.FlashSaleOutboxPersistenceAdapter;
import com.philia.flashsale.flashsale.outbox.application.model.OutboxEvent;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {"spring.liquibase.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(LiquibaseAutoConfiguration.class)
@Import({FlashSaleOutboxPersistenceAdapter.class, FlashSaleOutboxConcurrencyIntegrationTests.AdapterConfiguration.class})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FlashSaleOutboxConcurrencyIntegrationTests {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired JdbcTemplate jdbc;
    @Autowired FlashSaleOutboxPersistenceAdapter outbox;
    private ExecutorService workers;

    @TestConfiguration(proxyBeanMethods = false)
    static class AdapterConfiguration {
        @Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.autoconfigure.exclude", () -> "");
    }

    @AfterEach
    void clean() {
        if (workers != null) {
            workers.shutdownNow();
        }
        jdbc.update("DELETE FROM flash_sale_outbox_events");
    }

    @Test
    void twoWorkersCannotClaimTheSameLiveLease() throws Exception {
        List<UUID> ids = Stream.generate(UUID::randomUUID).limit(4).toList();
        ids.forEach(id -> insert(id, "PENDING", NOW, null, 0));
        workers = Executors.newFixedThreadPool(2);
        Future<List<OutboxEvent>> first = workers.submit(() -> outbox.claim("worker-a", NOW, 100,
                Duration.ofSeconds(30)));
        Future<List<OutboxEvent>> second = workers.submit(() -> outbox.claim("worker-b", NOW, 100,
                Duration.ofSeconds(30)));

        List<OutboxEvent> firstClaim = first.get();
        List<OutboxEvent> secondClaim = second.get();
        Set<UUID> firstIds = firstClaim.stream().map(OutboxEvent::eventId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> secondIds = secondClaim.stream().map(OutboxEvent::eventId).collect(java.util.stream.Collectors.toSet());

        Set<UUID> overlap = new HashSet<>(firstIds);
        overlap.retainAll(secondIds);
        assertThat(overlap).isEmpty();
        assertThat(Stream.concat(firstIds.stream(), secondIds.stream()).collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(ids);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flash_sale_outbox_events WHERE status = 'PROCESSING'",
                Integer.class)).isEqualTo(4);
    }

    @Test
    void expiredLeasesAreReclaimable() {
        UUID id = UUID.randomUUID();
        insert(id, "PROCESSING", NOW.minusSeconds(1), NOW.minusSeconds(1), 1);

        List<OutboxEvent> claimed = outbox.claim("worker-reclaimer", NOW, 100, Duration.ofSeconds(30));

        assertThat(claimed).extracting(OutboxEvent::eventId).containsExactly(id);
        assertThat(claimed.getFirst().attemptCount()).isEqualTo(2);
    }

    @Test
    void publishedAcknowledgementIsIdempotent() {
        UUID id = UUID.randomUUID();
        insert(id, "PENDING", NOW, null, 0);
        outbox.claim("worker-a", NOW, 100, Duration.ofSeconds(30));

        outbox.markPublished(id, NOW.plusSeconds(1));
        outbox.markPublished(id, NOW.plusSeconds(2));

        Map<String, Object> state = jdbc.queryForMap(
                "SELECT status, published_at, claim_until FROM flash_sale_outbox_events WHERE event_id = ?", id);
        assertThat(state.get("status")).isEqualTo("PUBLISHED");
        assertThat(state.get("published_at")).isNotNull();
        assertThat(state.get("claim_until")).isNull();
    }

    @Test
    void failedPublicationReturnsRowToPendingAndMakesItRecoverable() {
        UUID id = UUID.randomUUID();
        insert(id, "PENDING", NOW, null, 0);
        OutboxEvent claimed = outbox.claim("worker-a", NOW, 100, Duration.ofSeconds(30)).getFirst();

        outbox.markFailed(id, NOW, NOW.plusSeconds(1), "outbox publication failed: TimeoutException");
        List<OutboxEvent> recovered = outbox.claim("worker-b", NOW.plusSeconds(2), 100, Duration.ofSeconds(30));

        assertThat(claimed.eventId()).isEqualTo(id);
        assertThat(recovered).extracting(OutboxEvent::eventId).containsExactly(id);
        assertThat(recovered.getFirst().attemptCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT last_error FROM flash_sale_outbox_events WHERE event_id = ?",
                String.class, id)).isEqualTo("outbox publication failed: TimeoutException");
    }

    private void insert(UUID eventId, String status, Instant nextAttemptAt, Instant claimUntil, int attempts) {
        UUID aggregateId = UUID.randomUUID();
        try {
            String payload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                    "purchaseRequestId", aggregateId.toString(),
                    "reservationId", UUID.randomUUID().toString(),
                    "campaignId", UUID.randomUUID().toString(),
                    "variantId", UUID.randomUUID().toString(),
                    "userId", UUID.randomUUID().toString(),
                    "quantity", 1,
                    "unitPrice", "19.9900",
                    "currency", "VND",
                    "acceptedAt", NOW.toString(),
                    "expiresAt", NOW.plusSeconds(300).toString()));
            jdbc.update("""
                    INSERT INTO flash_sale_outbox_events
                    (event_id, aggregate_type, aggregate_id, aggregate_version, event_type, event_version,
                     payload, status, attempt_count, next_attempt_at, claimed_by, claim_until,
                     published_at, last_error, created_at, updated_at)
                    VALUES (?, 'PURCHASE_REQUEST', ?, 1, 'PurchaseAccepted', 1, ?::jsonb, ?, ?, ?,
                            NULL, ?, NULL, NULL, ?, ?)
                    """, eventId, aggregateId, payload, status, attempts, Timestamp.from(nextAttemptAt),
                    claimUntil == null ? null : Timestamp.from(claimUntil), Timestamp.from(NOW), Timestamp.from(NOW));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
