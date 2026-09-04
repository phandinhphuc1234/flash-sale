package com.philia.flashsale.cart.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {
        "spring.liquibase.enabled=true",
        "spring.liquibase.change-log=classpath:/db/changelog/db.changelog-master.yaml",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers(disabledWithoutDocker = true)
class CartMigrationCompatibilityIntegrationTests {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("cart_db").withUsername("flashsale").withPassword("test");

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void expandedSchemaIsForwardCompatibleAndLiquibaseHistoryIsRecorded() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery(
                    "select count(*) from information_schema.tables "
                            + "where table_name in ('carts','cart_items','cart_reconciliation_inbox')")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(3);
            }
            try (var result = statement.executeQuery(
                    "select count(*) from databasechangelog "
                            + "where id in ('001-create-cart-schema','002-add-checkout-revisions-and-inbox')")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(2);
            }
            try (var result = statement.executeQuery("""
                    select count(*) from information_schema.columns
                    where table_name in ('carts', 'cart_items') and column_name = 'version'
                    """)) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(2);
            }
        }
    }
}
