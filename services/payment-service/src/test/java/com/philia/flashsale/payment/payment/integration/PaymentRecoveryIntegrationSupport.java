package com.philia.flashsale.payment.payment.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Shared PostgreSQL harness for deterministic recovery-window proofs. */
@Testcontainers(disabledWithoutDocker = true)
abstract class PaymentRecoveryIntegrationSupport {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db").withUsername("flashsale").withPassword("test-password");

    @BeforeEach
    void resetRecoverySchema() throws Exception {
        try (Connection connection = connection()) {
            runLiquibase(connection);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("truncate payment_outbox_events, payment_recovery_work, "
                        + "payment_client_idempotency, payment_provider_event_receipts, "
                        + "payment_command_inbox, payment_attempts, payments cascade");
            }
        }
    }

    protected Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    protected UUID insertPayment(Connection connection, Instant deadline) throws SQLException {
        UUID paymentId = UUID.randomUUID();
        try (var insert = connection.prepareStatement("""
                insert into payments (id, order_id, user_id, amount, currency, payment_deadline, status,
                    aggregate_version, row_version, created_at, updated_at)
                values (?, ?, ?, 100000, 'VND', ?, 'PROCESSING', 0, 0, ?, ?)
                """)) {
            Instant now = Instant.now();
            insert.setObject(1, paymentId);
            insert.setObject(2, UUID.randomUUID());
            insert.setObject(3, UUID.randomUUID());
            insert.setTimestamp(4, java.sql.Timestamp.from(deadline));
            insert.setTimestamp(5, java.sql.Timestamp.from(now));
            insert.setTimestamp(6, java.sql.Timestamp.from(now));
            insert.executeUpdate();
        }
        return paymentId;
    }

    protected UUID insertAttempt(Connection connection, UUID paymentId, String status,
            String providerKey, String sessionId, Instant safeReplayUntil) throws SQLException {
        UUID attemptId = UUID.randomUUID();
        try (var insert = connection.prepareStatement("""
                insert into payment_attempts (id, payment_id, attempt_number, status, provider,
                    provider_idempotency_key, provider_session_id, first_submitted_at,
                    safe_replay_until, created_at, updated_at)
                values (?, ?, 1, ?, 'STRIPE', ?, ?, ?, ?, ?, ?)
                """)) {
            Instant now = Instant.now();
            insert.setObject(1, attemptId);
            insert.setObject(2, paymentId);
            insert.setString(3, status);
            insert.setString(4, providerKey);
            insert.setString(5, sessionId);
            insert.setTimestamp(6, java.sql.Timestamp.from(now));
            insert.setTimestamp(7, java.sql.Timestamp.from(safeReplayUntil));
            insert.setTimestamp(8, java.sql.Timestamp.from(now));
            insert.setTimestamp(9, java.sql.Timestamp.from(now));
            insert.executeUpdate();
        }
        return attemptId;
    }

    protected UUID insertWork(Connection connection, UUID paymentId, UUID attemptId, String type,
            String status, Instant nextAttemptAt, Instant leaseUntil, int attemptCount) throws SQLException {
        UUID workId = UUID.randomUUID();
        try (var insert = connection.prepareStatement("""
                insert into payment_recovery_work (id, payment_id, attempt_id, work_type, status,
                    provider_idempotency_key, safe_replay_until, attempt_count, next_attempt_at,
                    lease_until, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            Instant now = Instant.now();
            insert.setObject(1, workId);
            insert.setObject(2, paymentId);
            insert.setObject(3, attemptId);
            insert.setString(4, type);
            insert.setString(5, status);
            insert.setString(6, "provider-key");
            insert.setTimestamp(7, java.sql.Timestamp.from(now.plusSeconds(23 * 60 * 60)));
            insert.setInt(8, attemptCount);
            insert.setTimestamp(9, java.sql.Timestamp.from(nextAttemptAt));
            if (leaseUntil == null) insert.setObject(10, null);
            else insert.setTimestamp(10, java.sql.Timestamp.from(leaseUntil));
            insert.setTimestamp(11, java.sql.Timestamp.from(now));
            insert.setTimestamp(12, java.sql.Timestamp.from(now));
            insert.executeUpdate();
        }
        return workId;
    }

    private void runLiquibase(Connection connection) throws Exception {
        Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.yaml",
                new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
        liquibase.update(new Contexts(), new LabelExpression());
    }
}
