package com.philia.flashsale.order.integration;

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

/** Checks that regular checkout expands the Order schema without removing legacy Flash Sale fields. */
@Testcontainers(disabledWithoutDocker = true)
class RegularPurchaseMigrationIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("order_regular_purchase_db")
            .withUsername("order")
            .withPassword("order");

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
    void keepsLegacyIdentifiersAndAddsRegularPurchaseIntakeBoundary() {
        assertThat(tableExists("regular_purchase_requests")).isTrue();
        assertThat(columnExists("orders", "reservation_id")).isTrue();
        assertThat(columnExists("orders", "campaign_id")).isTrue();
        assertThat(columnExists("orders", "purchase_source")).isTrue();
        assertThat(columnExists("orders", "stock_participant_type")).isTrue();
        assertThat(columnExists("orders", "stock_reference_id")).isTrue();
        assertThat(columnExists("purchase_sagas", "stock_reference_id")).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from databasechangelog where id = '003-add-regular-purchase-checkout'",
                Integer.class)).isEqualTo(1);
    }

    private static boolean tableExists(String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from information_schema.tables
                    where table_schema = 'public' and table_name = ?
                )
                """, Boolean.class, table));
    }

    private static boolean columnExists(String table, String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from information_schema.columns
                    where table_schema = 'public' and table_name = ? and column_name = ?
                )
                """, Boolean.class, table, column));
    }
}
