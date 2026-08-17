package com.philia.flashsale.payment.payment.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import jakarta.persistence.EntityManagerFactory;

/** Boots Hibernate against the Liquibase-owned PostgreSQL schema to catch mapping drift. */
@DataJpaTest(properties = {
        "spring.liquibase.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PaymentJpaSchemaAgreementIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db")
            .withUsername("flashsale")
            .withPassword("test-password");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void hibernateValidatesAgainstLiquibaseSchema() {
        assertNotNull(entityManagerFactory.getMetamodel().entity(
                com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentJpaEntity.class));
        assertNotNull(entityManagerFactory.getMetamodel().entity(
                com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentAttemptJpaEntity.class));
    }
}
