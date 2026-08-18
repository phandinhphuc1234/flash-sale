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

    private void insertWorkIfDuplicateIsRejected(java.sql.Connection connection, UUID paymentId,
            UUID attemptId) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertWork(connection, paymentId, attemptId,
                "CREATE_SESSION", "PENDING", Instant.now(), null, 0))
                .hasMessageContaining("uk_payment_recovery_active_work");
    }
}
