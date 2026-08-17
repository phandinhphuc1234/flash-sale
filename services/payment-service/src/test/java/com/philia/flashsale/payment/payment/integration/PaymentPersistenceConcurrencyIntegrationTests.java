package com.philia.flashsale.payment.payment.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL evidence for partial uniqueness and reclaimable worker claims. */
@Testcontainers(disabledWithoutDocker = true)
class PaymentPersistenceConcurrencyIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db")
            .withUsername("flashsale")
            .withPassword("test-password");

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = connection()) {
            runLiquibase(connection);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("truncate payment_outbox_events, payment_recovery_work, payment_attempts, payments cascade");
            }
        }
    }

    @Test
    void partialUniqueIndexRejectsTwoUnresolvedAttemptsForOnePayment() throws Exception {
        UUID paymentId = insertPayment();
        insertAttempt(paymentId, "CREATING", "key-1");

        try (Connection connection = connection(); var statement = connection.prepareStatement("""
                insert into payment_attempts (id, payment_id, attempt_number, status, provider,
                    provider_idempotency_key, row_version, created_at, updated_at)
                values (?, ?, 2, 'UNKNOWN', 'STRIPE', 'key-2', 0, now(), now())
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, paymentId);
            PSQLException violation = org.junit.jupiter.api.Assertions.assertThrows(PSQLException.class,
                    statement::executeUpdate);
            assertTrue(violation.getMessage().contains("uk_payment_attempt_one_unresolved"));
        }
    }

    @Test
    void skipLockedClaimAndStaleLeaseRecoveryAreDurable() throws Exception {
        UUID paymentId = insertPayment();
        UUID workId = UUID.randomUUID();
        try (Connection first = connection(); Connection second = connection()) {
            insertRecoveryWork(workId, paymentId, "PENDING", Instant.now().minusSeconds(1), null);
            first.setAutoCommit(false);
            try (var claim = first.prepareStatement("""
                    select id from payment_recovery_work
                    where status = 'PENDING' and next_attempt_at <= now()
                    order by next_attempt_at for update skip locked limit 1
                    """)) {
                try (ResultSet rows = claim.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(workId, rows.getObject(1, UUID.class));
                }
            }

            second.setAutoCommit(false);
            try (var claim = second.prepareStatement("""
                    select id from payment_recovery_work
                    where status = 'PENDING' and next_attempt_at <= now()
                    order by next_attempt_at for update skip locked limit 1
                    """)) {
                try (ResultSet rows = claim.executeQuery()) {
                    assertTrue(!rows.next(), "a second worker must not claim a locked row");
                }
            }
            first.rollback();
            second.rollback();
        }

        try (Connection connection = connection(); var update = connection.prepareStatement("""
                update payment_recovery_work
                set status = 'IN_PROGRESS', lease_until = now() - interval '1 minute'
                where id = ?
                """)) {
            update.setObject(1, workId);
            update.executeUpdate();
        }
        try (Connection connection = connection(); var claim = connection.prepareStatement("""
                select id from payment_recovery_work
                where status = 'IN_PROGRESS' and lease_until < now()
                for update skip locked
                """)) {
            try (ResultSet rows = claim.executeQuery()) {
                assertTrue(rows.next(), "stale lease must be reclaimable");
            }
        }
    }

    @Test
    void outboxClaimUsesTheSameReclaimableLeasePattern() throws Exception {
        UUID paymentId = insertPayment();
        try (Connection connection = connection(); var insert = connection.prepareStatement("""
                insert into payment_outbox_events (event_id, aggregate_id, aggregate_version, event_type,
                    event_version, topic_name, message_key, payload, status, attempt_count, next_attempt_at, created_at)
                values (?, ?, 1, 'PaymentFailed', 1, 'flashsale.payment.events.v1', ?, '{}'::jsonb,
                    'PENDING', 0, now(), now())
                """)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, paymentId);
            insert.setObject(3, UUID.randomUUID());
            insert.executeUpdate();
        }
        try (Connection connection = connection(); var claim = connection.prepareStatement("""
                select event_id from payment_outbox_events
                where status = 'PENDING' and next_attempt_at <= now()
                for update skip locked limit 1
                """)) {
            try (ResultSet rows = claim.executeQuery()) {
                assertTrue(rows.next());
            }
        }
    }

    private UUID insertPayment() throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = connection(); var insert = connection.prepareStatement("""
                insert into payments (id, order_id, user_id, amount, currency, payment_deadline, status,
                    aggregate_version, row_version, created_at, updated_at)
                values (?, ?, ?, 100.0000, 'VND', now() + interval '5 minutes', 'PENDING', 0, 0, now(), now())
                """)) {
            insert.setObject(1, id);
            insert.setObject(2, UUID.randomUUID());
            insert.setObject(3, UUID.randomUUID());
            insert.executeUpdate();
        }
        return id;
    }

    private void insertAttempt(UUID paymentId, String status, String providerKey) throws Exception {
        try (Connection connection = connection(); var insert = connection.prepareStatement("""
                insert into payment_attempts (id, payment_id, attempt_number, status, provider,
                    provider_idempotency_key, row_version, created_at, updated_at)
                values (?, ?, 1, ?, 'STRIPE', ?, 0, now(), now())
                """)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, paymentId);
            insert.setString(3, status);
            insert.setString(4, providerKey);
            insert.executeUpdate();
        }
    }

    private void insertRecoveryWork(UUID id, UUID paymentId, String status, Instant nextAttemptAt,
            Instant leaseUntil) throws Exception {
        try (Connection connection = connection(); var insert = connection.prepareStatement("""
                insert into payment_recovery_work (id, payment_id, work_type, status, attempt_count,
                    next_attempt_at, lease_until, created_at, updated_at)
                values (?, ?, 'REFRESH_SESSION', ?, 0, ?, ?, now(), now())
                """)) {
            insert.setObject(1, id);
            insert.setObject(2, paymentId);
            insert.setString(3, status);
            insert.setTimestamp(4, java.sql.Timestamp.from(nextAttemptAt));
            if (leaseUntil == null) {
                insert.setNull(5, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                insert.setTimestamp(5, java.sql.Timestamp.from(leaseUntil));
            }
            insert.executeUpdate();
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void runLiquibase(Connection connection) throws Exception {
        Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.yaml",
                new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
        liquibase.update(new Contexts(), new LabelExpression());
    }
}
