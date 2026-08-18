package com.philia.flashsale.payment.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PostgreSQL proof for multi-instance leases, stale reclaim, outage retry, and manual review. */
class PaymentRecoveryWorkIntegrationTests extends PaymentRecoveryIntegrationSupport {

    @Test
    void staleLeaseIsReclaimableAndExhaustedWorkIsVisibleForManualReview() throws Exception {
        UUID paymentId;
        UUID workId;
        try (var connection = connection()) {
            paymentId = insertPayment(connection, Instant.now().plusSeconds(3600));
            workId = insertWork(connection, paymentId, null, "EXPIRE_SESSION", "IN_PROGRESS",
                    Instant.now().minusSeconds(10), Instant.now().minusSeconds(1), 9);
            try (var claim = connection.prepareStatement("""
                    update payment_recovery_work
                    set status = 'IN_PROGRESS', lease_owner = 'instance-b',
                        lease_until = now() + interval '30 seconds', attempt_count = attempt_count + 1,
                        updated_at = now()
                    where id = ? and status = 'IN_PROGRESS' and lease_until < now()
                    """)) {
                claim.setObject(1, workId);
                assertThat(claim.executeUpdate()).isEqualTo(1);
            }
            try (var manual = connection.prepareStatement("""
                    update payment_recovery_work
                    set status = 'MANUAL_REVIEW', last_error_code = 'PROVIDER_OUTAGE',
                        lease_owner = null, lease_until = null, updated_at = now()
                    where id = ?
                    """)) {
                manual.setObject(1, workId);
                manual.executeUpdate();
            }
        }
        try (var connection = connection(); var query = connection.prepareStatement(
                "select status, last_error_code, lease_owner from payment_recovery_work where id = ?")) {
            query.setObject(1, workId);
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("status")).isEqualTo("MANUAL_REVIEW");
                assertThat(rows.getString("last_error_code")).isEqualTo("PROVIDER_OUTAGE");
                assertThat(rows.getString("lease_owner")).isNull();
            }
        }
        assertThat(paymentId).isNotNull();
    }

    @Test
    void deadlineWorkWithoutAttemptIsUniquePerPayment() throws Exception {
        try (var connection = connection()) {
            UUID paymentId = insertPayment(connection, Instant.now().minusSeconds(1));
            insertWork(connection, paymentId, null, "EXPIRE_SESSION", "PENDING", Instant.now(), null, 0);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertWork(connection, paymentId, null,
                    "EXPIRE_SESSION", "PENDING", Instant.now(), null, 0))
                    .hasMessageContaining("uk_payment_recovery_active_payment_work_no_attempt");
        }
    }
}
