package com.philia.flashsale.payment.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PostgreSQL proof that a verified late success is monotonic and never creates a refund. */
class PaymentLateSuccessIntegrationTests extends PaymentRecoveryIntegrationSupport {

    @Test
    void lateVerifiedSuccessWinsWithOneHigherVersionedFact() throws Exception {
        UUID paymentId;
        try (var connection = connection()) {
            paymentId = insertPayment(connection, Instant.now().minusSeconds(1));
            UUID attemptId = insertAttempt(connection, paymentId, "FAILED", "late-key", "cs_late",
                    Instant.now().plusSeconds(3600));
            try (var update = connection.prepareStatement("""
                    update payments set status = 'SUCCEEDED', failure_reason = null,
                        succeeded_at = now(), aggregate_version = 2, updated_at = now()
                    where id = ?
                    """)) {
                update.setObject(1, paymentId);
                update.executeUpdate();
            }
            try (var outbox = connection.prepareStatement("""
                    insert into payment_outbox_events (event_id, aggregate_id, aggregate_version,
                        event_type, event_version, topic_name, message_key, payload,
                        status, attempt_count, next_attempt_at, created_at)
                    values (?, ?, 2, 'PaymentSucceeded', 1, 'flashsale.payment.events.v1', ?, '{}'::jsonb,
                        'PENDING', 0, now(), now())
                    """)) {
                outbox.setObject(1, UUID.randomUUID());
                outbox.setObject(2, paymentId);
                outbox.setObject(3, UUID.randomUUID());
                outbox.executeUpdate();
            }
            assertThat(attemptId).isNotNull();
        }
        try (var connection = connection(); var query = connection.prepareStatement("""
                select p.status, p.aggregate_version,
                       (select count(*) from payment_outbox_events where aggregate_id = ?) as facts,
                       to_regclass('payment_refunds') as refunds
                from payments p where p.id = ?
                """)) {
            query.setObject(1, paymentId);
            query.setObject(2, paymentId);
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("status")).isEqualTo("SUCCEEDED");
                assertThat(rows.getLong("aggregate_version")).isEqualTo(2);
                assertThat(rows.getInt("facts")).isEqualTo(1);
                assertThat(rows.getString("refunds")).isNull();
            }
        }
    }
}
