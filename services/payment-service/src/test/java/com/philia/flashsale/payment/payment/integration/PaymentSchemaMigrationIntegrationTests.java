package com.philia.flashsale.payment.payment.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL evidence for the Payment-owned schema and its safety constraints. */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentSchemaMigrationIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db")
            .withUsername("flashsale")
            .withPassword("test-password");

    @Test
    @Order(1)
    void liquibaseCreatesPaymentTablesIndexesAndSafeColumns() throws Exception {
        try (Connection connection = connection()) {
            runLiquibase(connection);

            Set<String> tables = new HashSet<>();
            try (ResultSet result = connection.getMetaData().getTables(null, "public", "%", new String[] {"TABLE"})) {
                while (result.next()) {
                    tables.add(result.getString("TABLE_NAME"));
                }
            }

            assertTrue(tables.containsAll(Set.of("payments", "payment_attempts", "payment_command_inbox",
                    "payment_client_idempotency", "payment_provider_event_receipts", "payment_recovery_work",
                    "payment_outbox_events")));

            try (var statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("""
                            select numeric_precision, numeric_scale
                            from information_schema.columns
                            where table_name = 'payments' and column_name = 'amount'
                            """)) {
                assertTrue(result.next());
                assertEquals(19, result.getInt(1));
                assertEquals(4, result.getInt(2));
            }

            try (var statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("""
                            select indexdef from pg_indexes
                            where tablename = 'payment_attempts'
                              and indexname = 'uk_payment_attempt_one_unresolved'
                            """)) {
                assertTrue(result.next());
                String indexDefinition = result.getString(1);
                assertTrue(indexDefinition.contains("payment_id"));
                assertTrue(indexDefinition.contains("CREATING"));
                assertTrue(indexDefinition.contains("UNKNOWN"));
            }

            try (var statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("""
                            select table_name, column_name
                            from information_schema.columns
                            where table_name in ('payments', 'payment_attempts', 'payment_provider_event_receipts',
                                'payment_outbox_events')
                              and lower(column_name) like any (array['%url%', '%raw%body%', '%signature%', '%secret%'])
                            """)) {
                assertTrue(!result.next(), "sensitive provider payload columns must not exist");
            }
        }
    }

    @Test
    @Order(2)
    void databaseTransactionRollbackLeavesNoPartialPaymentRows() throws Exception {
        try (Connection connection = connection()) {
            runLiquibase(connection);
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("""
                    insert into payments (id, order_id, user_id, amount, currency, payment_deadline,
                        status, aggregate_version, row_version, created_at, updated_at)
                    values (?, ?, ?, 100.0000, 'VND', now(),
                        'PENDING', 0, 0, now(), now())
                    """)) {
                insert.setObject(1, java.util.UUID.randomUUID());
                insert.setObject(2, java.util.UUID.randomUUID());
                insert.setObject(3, java.util.UUID.randomUUID());
                insert.executeUpdate();
            }
            connection.rollback();
            connection.setAutoCommit(true);
            try (var query = connection.createStatement();
                    ResultSet result = query.executeQuery("select count(*) from payments")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
    }

    @Test
    @Order(3)
    void emptyFeatureSchemaSupportsExplicitLiquibaseRollback() throws Exception {
        try (Connection connection = connection()) {
            Liquibase liquibase = runLiquibase(connection);
            liquibase.rollback(3, new Contexts(), new LabelExpression());
            try (var statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("""
                            select count(*) from information_schema.tables
                            where table_schema = 'public' and table_name = 'payments'
                            """)) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private Liquibase runLiquibase(Connection connection) throws Exception {
        Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.yaml",
                new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
        liquibase.update(new Contexts(), new LabelExpression());
        return liquibase;
    }
}
