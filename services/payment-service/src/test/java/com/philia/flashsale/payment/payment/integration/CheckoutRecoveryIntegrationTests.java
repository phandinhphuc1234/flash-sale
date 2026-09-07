package com.philia.flashsale.payment.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PostgreSQL proof that response loss/process interruption preserve one replay identity. */
class CheckoutRecoveryIntegrationTests extends PaymentRecoveryIntegrationSupport {

    @Test
    void responseLossLeavesOneAttemptAndOneStableRecoveryWork() throws Exception {
        UUID paymentId;
        UUID attemptId;
        try (var connection = connection()) {
            paymentId = insertPayment(connection, Instant.now().plusSeconds(3600));
            attemptId = insertAttempt(connection, paymentId, "CREATING", "provider-key", null,
                    Instant.now().plusSeconds(23 * 60 * 60));
            insertWork(connection, paymentId, attemptId, "CREATE_SESSION", "PENDING", Instant.now(), null, 0);
            insertWorkIfDuplicateIsRejected(connection, paymentId, attemptId);
        }
        try (var connection = connection(); var query = connection.prepareStatement("""
                select (select count(*) from payment_attempts where payment_id = ?) as attempts,
                       (select count(*) from payment_recovery_work where payment_id = ?) as work,
                       (select count(distinct provider_idempotency_key) from payment_recovery_work where payment_id = ?) as keys
                """)) {
            query.setObject(1, paymentId);
            query.setObject(2, paymentId);
            query.setObject(3, paymentId);
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("attempts")).isEqualTo(1);
                assertThat(rows.getInt("work")).isEqualTo(1);
                assertThat(rows.getInt("keys")).isEqualTo(1);
            }
        }
        assertThat(attemptId).isNotNull();
    }

    @Test
    void expiredPaymentRetainsProviderAttemptAndRecoveryIdentity() throws Exception {
        UUID paymentId;
        UUID attemptId;
        Instant deadline = Instant.now().minusSeconds(1);
        try (var connection = connection()) {
            paymentId = insertPayment(connection, deadline);
            attemptId = insertAttempt(connection, paymentId, "EXPIRED", "late-provider-key", "cs_late",
                    Instant.now().plusSeconds(3600));
            UUID workId = insertWork(connection, paymentId, attemptId, "REFRESH_SESSION", "PENDING",
                    Instant.now(), null, 0);
            try (var update = connection.prepareStatement("""
                    update payment_recovery_work
                       set provider_idempotency_key = ?
                     where id = ?
                    """)) {
                update.setString(1, "late-provider-key");
                update.setObject(2, workId);
                update.executeUpdate();
            }
            try (var update = connection.prepareStatement("""
                    update payments
                       set status = 'EXPIRED', failure_reason = 'PAYMENT_DEADLINE_EXPIRED',
                           aggregate_version = 1, updated_at = ?
                     where id = ?
                    """)) {
                update.setTimestamp(1, java.sql.Timestamp.from(deadline));
                update.setObject(2, paymentId);
                update.executeUpdate();
            }
        }

        try (var connection = connection(); var query = connection.prepareStatement("""
                select p.status, p.failure_reason, a.status, a.provider_session_id,
                       a.provider_idempotency_key, w.status, w.provider_idempotency_key
                  from payments p
                  join payment_attempts a on a.payment_id = p.id
                  join payment_recovery_work w on w.payment_id = p.id and w.attempt_id = a.id
                 where p.id = ?
                """)) {
            query.setObject(1, paymentId);
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("EXPIRED");
                assertThat(rows.getString(2)).isEqualTo("PAYMENT_DEADLINE_EXPIRED");
                assertThat(rows.getString(3)).isEqualTo("EXPIRED");
                assertThat(rows.getString(4)).isEqualTo("cs_late");
                assertThat(rows.getString(5)).isEqualTo("late-provider-key");
                assertThat(rows.getString(6)).isEqualTo("PENDING");
                assertThat(rows.getString(7)).isEqualTo("late-provider-key");
                assertThat(attemptId).isNotNull();
            }
        }
    }

    private void insertWorkIfDuplicateIsRejected(java.sql.Connection connection, UUID paymentId,
            UUID attemptId) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertWork(connection, paymentId, attemptId,
                "CREATE_SESSION", "PENDING", Instant.now(), null, 0))
                .hasMessageContaining("uk_payment_recovery_active_work");
    }
}
