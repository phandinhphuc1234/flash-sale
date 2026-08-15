package com.philia.flashsale.order.outbox.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.order.outbox.adapter.out.persistence.OrderOutboxPersistenceAdapter;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.support.PostgreSqlIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/** PostgreSQL evidence for lease ownership, recovery, and stable publication identity. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.bootstrap-servers=localhost:19092",
        "order.runtime.accepted-purchase-consumer-enabled=false",
        "order.runtime.outbox-publisher-enabled=true",
        "order.outbox.enabled=true",
        "order.outbox.poll-interval=1h"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderOutboxConcurrencyIntegrationTests extends PostgreSqlIntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Autowired
    private OrderOutboxPersistenceAdapter persistence;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM order_outbox_events");
    }

    @Test
    void multipleWorkersClaimEachDueRowOnlyOnce() throws Exception {
        UUID eventId = insert("PENDING", 0, NOW, null, null);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<List<OrderOutboxEvent>> first = CompletableFuture.supplyAsync(
                    () -> persistence.claim("worker-a", NOW, 1, Duration.ofSeconds(30)), workers);
            CompletableFuture<List<OrderOutboxEvent>> second = CompletableFuture.supplyAsync(
                    () -> persistence.claim("worker-b", NOW, 1, Duration.ofSeconds(30)), workers);

            List<OrderOutboxEvent> firstClaim = first.get(10, TimeUnit.SECONDS);
            List<OrderOutboxEvent> secondClaim = second.get(10, TimeUnit.SECONDS);

            assertThat(firstClaim.size() + secondClaim.size()).isEqualTo(1);
            OrderOutboxEvent claimed = firstClaim.isEmpty() ? secondClaim.get(0) : firstClaim.get(0);
            assertThat(claimed.eventId()).isEqualTo(eventId);
            assertThat(claimed.attemptCount()).isEqualTo(1);
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void expiredLeaseIsRecoveredByAnotherWorker() {
        UUID eventId = insert("IN_PROGRESS", 1, NOW.minusSeconds(60), "dead-worker", NOW.minusSeconds(30));

        List<OrderOutboxEvent> recovered = persistence.claim("recovery-worker", NOW, 1, Duration.ofSeconds(30));

        assertThat(recovered).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isEqualTo(eventId);
            assertThat(event.claimedBy()).isEqualTo("recovery-worker");
            assertThat(event.attemptCount()).isEqualTo(2);
        });
    }

    @Test
    void staleWorkerCannotMarkARecoveredLeaseAndIdentityRemainsStable() {
        UUID eventId = insert("PENDING", 0, NOW, null, null);
        OrderOutboxEvent first = persistence.claim("worker-a", NOW, 1, Duration.ZERO.plusSeconds(30)).get(0);

        jdbc.update("UPDATE order_outbox_events SET claim_until = ? WHERE event_id = ?",
                Timestamp.from(NOW.minusSeconds(1)), eventId);
        OrderOutboxEvent recovered = persistence.claim("worker-b", NOW, 1, Duration.ofSeconds(30)).get(0);

        persistence.markPublished(eventId, "worker-a", NOW.plusSeconds(1));
        assertThat(jdbc.queryForObject("SELECT status FROM order_outbox_events WHERE event_id = ?", String.class, eventId))
                .isEqualTo("IN_PROGRESS");

        persistence.markPublished(eventId, "worker-b", NOW.plusSeconds(2));
        assertThat(jdbc.queryForObject("SELECT status FROM order_outbox_events WHERE event_id = ?", String.class, eventId))
                .isEqualTo("PUBLISHED");
        assertThat(recovered.eventId()).isEqualTo(first.eventId()).isEqualTo(eventId);
    }

    @Test
    void failedPublicationReturnsTheSameIdentityToTheDueRetryQueue() {
        UUID eventId = insert("PENDING", 0, NOW, null, null);
        OrderOutboxEvent claimed = persistence.claim("worker-a", NOW, 1, Duration.ofSeconds(30)).get(0);

        persistence.markFailed(eventId, "worker-a", NOW, NOW.plusSeconds(2), "KafkaException");

        assertThat(jdbc.queryForObject(
                "SELECT status || ':' || attempt_count || ':' || COALESCE(claimed_by, 'null') || ':' || last_error "
                        + "FROM order_outbox_events WHERE event_id = ?", String.class, eventId))
                .isEqualTo("PENDING:1:null:KafkaException");
        assertThat(jdbc.queryForObject(
                "SELECT next_attempt_at FROM order_outbox_events WHERE event_id = ?", Timestamp.class, eventId)
                .toInstant()).isEqualTo(NOW.plusSeconds(2));
        assertThat(claimed.eventId()).isEqualTo(eventId);
    }

    private UUID insert(String status, int attemptCount, Instant nextAttemptAt, String claimedBy,
            Instant claimUntil) {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO order_outbox_events (
                    event_id, aggregate_type, aggregate_id, aggregate_version, event_type, event_version,
                    event_key, correlation_id, causation_id, payload, traceparent, tracestate,
                    status, attempt_count, next_attempt_at, claimed_by, claim_until,
                    occurred_at, created_at, updated_at
                ) VALUES (?, 'ORDER', ?, 1, 'OrderCreated', 1, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, eventId, orderId, orderId.toString(), UUID.randomUUID(), UUID.randomUUID(),
                "{\"orderId\":\"" + orderId + "\"}",
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01", null, status, attemptCount,
                Timestamp.from(nextAttemptAt), claimedBy, claimUntil == null ? null : Timestamp.from(claimUntil),
                Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW));
        return eventId;
    }
}
