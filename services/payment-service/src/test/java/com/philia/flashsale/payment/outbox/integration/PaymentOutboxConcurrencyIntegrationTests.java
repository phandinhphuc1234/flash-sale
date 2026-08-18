package com.philia.flashsale.payment.outbox.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.payment.outbox.application.port.ClaimPaymentOutboxPort;
import com.philia.flashsale.payment.outbox.application.port.UpdatePaymentOutboxPort;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL proof for outbox ownership, retry, lease recovery, and stable identity. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "payment.acceptance.enabled=true",
        "payment.checkout.enabled=false",
        "payment.stripe.enabled=false",
        "payment.kafka.consumer-enabled=false",
        "payment.kafka.outbox-publisher-enabled=false",
        "payment.webhook.processing.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class PaymentOutboxConcurrencyIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-08-18T09:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db")
            .withUsername("flashsale")
            .withPassword("test-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ClaimPaymentOutboxPort claimOutbox;

    @Autowired
    private UpdatePaymentOutboxPort updateOutbox;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("truncate payment_outbox_events, payment_recovery_work, payment_provider_event_receipts, "
                + "payment_client_idempotency, payment_command_inbox, payment_attempts, payments cascade");
    }

    @Test
    void twoWorkersCannotClaimTheSameRowAndFailureRequeuesWithTheSameIdentity() {
        UUID paymentId = insertPayment();
        UUID eventId = insertOutbox(paymentId, NOW);

        var first = claimOutbox.claimBatch(NOW, 10, "worker-a", NOW.plusSeconds(30));
        var second = claimOutbox.claimBatch(NOW, 10, "worker-b", NOW.plusSeconds(30));

        assertThat(first).extracting(event -> event.eventId()).containsExactly(eventId);
        assertThat(second).isEmpty();
        assertThat(updateOutbox.recordFailure(eventId, "worker-a", NOW,
                "BROKER_UNAVAILABLE", NOW.plusSeconds(2))).isTrue();
        assertThat(jdbc.queryForObject("select status from payment_outbox_events where event_id = ?",
                String.class, eventId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select last_error_code from payment_outbox_events where event_id = ?",
                String.class, eventId)).isEqualTo("BROKER_UNAVAILABLE");

        var reclaimed = claimOutbox.claimBatch(NOW.plusSeconds(3), 10, "worker-b", NOW.plusSeconds(33));
        assertThat(reclaimed).extracting(event -> event.eventId()).containsExactly(eventId);
        assertThat(reclaimed.get(0).attemptCount()).isEqualTo(2);
    }

    @Test
    void expiredLeaseIsReclaimableAndWrongWorkerCannotAcknowledge() {
        UUID paymentId = insertPayment();
        UUID eventId = insertOutbox(paymentId, NOW);

        var first = claimOutbox.claimBatch(NOW, 10, "worker-a", NOW.plusSeconds(30));
        assertThat(first).extracting(event -> event.eventId()).containsExactly(eventId);
        assertThat(updateOutbox.markPublished(eventId, "wrong-worker", NOW.plusSeconds(1))).isFalse();

        var reclaimed = claimOutbox.claimBatch(NOW.plusSeconds(31), 10, "worker-b", NOW.plusSeconds(61));
        assertThat(reclaimed).extracting(event -> event.eventId()).containsExactly(eventId);
        assertThat(updateOutbox.markPublished(eventId, "worker-b", NOW.plusSeconds(32))).isTrue();
        assertThat(jdbc.queryForObject("select status from payment_outbox_events where event_id = ?",
                String.class, eventId)).isEqualTo("PUBLISHED");
    }

    private UUID insertPayment() {
        UUID paymentId = UUID.randomUUID();
        jdbc.update("""
                insert into payments (id, order_id, user_id, amount, currency, payment_deadline, status,
                    aggregate_version, row_version, created_at, updated_at)
                values (?, ?, ?, ?, 'VND', ?, 'PENDING', 0, 0, ?, ?)
                """, paymentId, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.0000"),
                Timestamp.from(NOW.plusSeconds(300)), Timestamp.from(NOW), Timestamp.from(NOW));
        return paymentId;
    }

    private UUID insertOutbox(UUID paymentId, Instant nextAttemptAt) {
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
                insert into payment_outbox_events (event_id, aggregate_id, aggregate_version, event_type,
                    event_version, topic_name, message_key, payload, status, attempt_count,
                    next_attempt_at, created_at)
                values (?, ?, 1, 'PaymentFailed', 1, 'flashsale.payment.events.v1', ?,
                    ?::jsonb, 'PENDING', 0, ?, ?)
                """, eventId, paymentId, UUID.randomUUID(),
                "{\"eventId\":\"" + eventId + "\",\"aggregateId\":\"" + paymentId
                        + "\",\"aggregateVersion\":1,\"eventType\":\"PaymentFailed\",\"eventVersion\":1}",
                Timestamp.from(nextAttemptAt), Timestamp.from(NOW));
        return eventId;
    }
}
