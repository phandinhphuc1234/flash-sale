package com.philia.flashsale.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies the additive Inventory-owned regular hold persistence boundary. */
@Testcontainers(disabledWithoutDocker = true)
class RegularHoldMigrationIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("inventory_db")
            .withUsername("inventory")
            .withPassword("inventory");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateDatabase() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:/db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
    }

    @Test
    void createsHoldTablesAndAdditiveOutboxEnvelopeColumns() {
        assertThat(tableCount("regular_stock_holds", "regular_stock_hold_items", "regular_hold_command_inbox"))
                .isEqualTo(3);
        assertThat(columnCount("outbox_events", "aggregate_version", "event_version", "event_key",
                "correlation_id", "causation_id", "traceparent", "tracestate", "next_attempt_at",
                "claimed_by", "claim_until")).isEqualTo(10);
        assertThat(jdbc.queryForObject(
                "select count(*) from databasechangelog where id = 'inventory-002-add-regular-stock-holds'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from databasechangelog where id = 'inventory-003-normalize-regular-hold-fingerprints'",
                Integer.class)).isEqualTo(1);
        assertThat(columnDataType("regular_stock_holds", "request_fingerprint"))
                .isEqualTo("character varying");
        assertThat(columnDataType("regular_hold_command_inbox", "payload_fingerprint"))
                .isEqualTo("character varying");
    }

    private static int tableCount(String... names) {
        return jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name = any (?::text[])
                """, Integer.class, "{" + String.join(",", names) + "}");
    }

    private static int columnCount(String table, String... names) {
        return jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = ? and column_name = any (?::text[])
                """, Integer.class, table, "{" + String.join(",", names) + "}");
    }

    private static String columnDataType(String table, String column) {
        return jdbc.queryForObject("""
                select data_type from information_schema.columns
                where table_schema = 'public' and table_name = ? and column_name = ?
                """, String.class, table, column);
    }
}
