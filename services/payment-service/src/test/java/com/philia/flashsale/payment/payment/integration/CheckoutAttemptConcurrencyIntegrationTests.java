package com.philia.flashsale.payment.payment.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

/** PostgreSQL proof for G5 client idempotency and one-unresolved-attempt invariants. */
@Testcontainers(disabledWithoutDocker = true)
class CheckoutAttemptConcurrencyIntegrationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db").withUsername("flashsale").withPassword("test-password");

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = connection()) {
            runLiquibase(connection);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("truncate payment_outbox_events, payment_recovery_work, "
                        + "payment_client_idempotency, payment_attempts, payments cascade");
            }
        }
    }

    @Test
    void onePaymentCannotHaveTwoUnresolvedAttemptsAndRawClientKeyHasNoStorageColumn() throws Exception {
        UUID paymentId = insertPayment();
        insertAttempt(paymentId, "CREATING", "provider-1");
        try (Connection connection = connection(); var statement = connection.prepareStatement("""
                insert into payment_attempts (id, payment_id, attempt_number, status, provider,
                    provider_idempotency_key, created_at, updated_at)
                values (?, ?, 2, 'UNKNOWN', 'STRIPE', 'provider-2', now(), now())
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, paymentId);
            PSQLException violation = assertThrows(PSQLException.class, statement::executeUpdate);
            assertTrue(violation.getMessage().contains("uk_payment_attempt_one_unresolved"));
        }
        try (Connection connection = connection(); var columns = connection.getMetaData()
                .getColumns(null, null, "payment_client_idempotency", "%")) {
            while (columns.next()) {
                assertFalse("raw_key".equalsIgnoreCase(columns.getString("COLUMN_NAME")));
            }
        }
    }

    @Test
    void oneHundredDifferentClientKeysRemainUniqueAndRollbackLeavesNoIdentity() throws Exception {
        UUID paymentId = insertPayment();
        int total = 100;
        var executor = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int index = 0; index < total; index++) {
                int keyNumber = index;
                jobs.add(() -> {
                    try (Connection connection = connection(); var insert = connection.prepareStatement("""
                            insert into payment_client_idempotency (id, operation, key_digest, user_id, payment_id,
                                request_fingerprint, outcome_status, created_at, updated_at)
                            values (?, 'CREATE_OR_RESUME_CHECKOUT', ?, ?, ?, ?, 'ACCEPTED', now(), now())
                            """)) {
                        insert.setObject(1, UUID.randomUUID());
                        insert.setString(2, String.format("%064d", keyNumber));
                        insert.setObject(3, UUID.randomUUID());
                        insert.setObject(4, paymentId);
                        insert.setString(5, String.format("%064d", keyNumber));
                        insert.executeUpdate();
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = executor.invokeAll(jobs);
            for (Future<Void> future : futures) future.get();
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        try (Connection connection = connection(); var count = connection.createStatement();
                ResultSet rows = count.executeQuery("select count(*) from payment_client_idempotency")) {
            assertTrue(rows.next());
            assertEquals(total, rows.getInt(1));
        }

        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("""
                    insert into payment_client_idempotency (id, operation, key_digest, user_id, payment_id,
                        request_fingerprint, outcome_status, created_at, updated_at)
                    values (?, 'CREATE_OR_RESUME_CHECKOUT', ?, ?, ?, ?, 'ACCEPTED', now(), now())
                    """)) {
                insert.setObject(1, UUID.randomUUID());
                insert.setString(2, "f".repeat(64));
                insert.setObject(3, UUID.randomUUID());
                insert.setObject(4, paymentId);
                insert.setString(5, "e".repeat(64));
                insert.executeUpdate();
            }
            connection.rollback();
        }
        try (Connection connection = connection(); var count = connection.createStatement();
                ResultSet rows = count.executeQuery("select count(*) from payment_client_idempotency where key_digest = '" + "f".repeat(64) + "'")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }

    @Test
    void responseLossLeavesDurableCreatingAttemptAndRecoveryWork() throws Exception {
        UUID paymentId = insertPayment();
        UUID attemptId = UUID.randomUUID();
        insertAttempt(paymentId, "CREATING", "stable-provider-key");
        try (Connection connection = connection(); var query = connection.prepareStatement("select id from payment_attempts where payment_id = ?")) {
            query.setObject(1, paymentId);
            try (ResultSet rows = query.executeQuery()) {
                assertTrue(rows.next());
                attemptId = rows.getObject(1, UUID.class);
            }
        }
        try (Connection connection = connection(); var insert = connection.prepareStatement("""
                insert into payment_recovery_work (id, payment_id, attempt_id, work_type, status,
                    provider_idempotency_key, safe_replay_until, next_attempt_at, created_at, updated_at)
                values (?, ?, ?, 'CREATE_SESSION', 'PENDING', 'stable-provider-key', ?, now(), now(), now())
                """)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, paymentId);
            insert.setObject(3, attemptId);
            insert.setTimestamp(4, java.sql.Timestamp.from(Instant.now().plusSeconds(3600)));
            insert.executeUpdate();
        }
        try (Connection connection = connection(); var query = connection.prepareStatement("select status, provider_idempotency_key from payment_recovery_work where attempt_id = ?")) {
            query.setObject(1, attemptId);
            try (ResultSet rows = query.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("PENDING", rows.getString(1));
                assertEquals("stable-provider-key", rows.getString(2));
            }
        }
    }

    private UUID insertPayment() throws SQLException {
        UUID paymentId = UUID.randomUUID();
        try (Connection connection = connection(); var insert = connection.prepareStatement("""
                insert into payments (id, order_id, user_id, amount, currency, payment_deadline, status,
                    aggregate_version, row_version, created_at, updated_at)
                values (?, ?, ?, 100000, 'VND', now() + interval '5 minutes', 'PENDING', 0, 0, now(), now())
                """)) {
            insert.setObject(1, paymentId);
            insert.setObject(2, UUID.randomUUID());
            insert.setObject(3, UUID.randomUUID());
            insert.executeUpdate();
        }
        return paymentId;
    }

    private void insertAttempt(UUID paymentId, String status, String providerKey) throws SQLException {
        try (Connection connection = connection(); var insert = connection.prepareStatement("""
                insert into payment_attempts (id, payment_id, attempt_number, status, provider,
                    provider_idempotency_key, created_at, updated_at)
                values (?, ?, 1, ?, 'STRIPE', ?, now(), now())
                """)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, paymentId);
            insert.setString(3, status);
            insert.setString(4, providerKey);
            insert.executeUpdate();
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void runLiquibase(Connection connection) throws Exception {
        Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.yaml",
                new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
        liquibase.update(new Contexts(), new LabelExpression());
    }
}
