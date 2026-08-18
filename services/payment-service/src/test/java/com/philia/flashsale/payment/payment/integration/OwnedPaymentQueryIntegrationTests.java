package com.philia.flashsale.payment.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.OwnedPaymentQueryJpaAdapter;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.OwnedPaymentQueryJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentAttemptJpaRepository;
import com.philia.flashsale.payment.payment.application.model.query.GetOwnedPaymentByOrderQuery;
import com.philia.flashsale.payment.payment.application.model.query.GetOwnedPaymentQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL proof for owner predicates, attempt count, and projection redaction. */
@DataJpaTest(properties = {
        "spring.liquibase.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class OwnedPaymentQueryIntegrationTests {
    private static final UUID OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_OWNER = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PAYMENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db").withUsername("flashsale").withPassword("test-password");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired OwnedPaymentQueryJpaRepository payments;
    @Autowired PaymentAttemptJpaRepository attempts;

    @BeforeEach
    void seed() {
        jdbc.update("delete from payment_attempts");
        jdbc.update("delete from payments");
        Instant now = Instant.parse("2026-08-17T15:25:00Z");
        jdbc.update("""
                insert into payments (id, order_id, user_id, amount, currency, payment_deadline,
                    status, aggregate_version, row_version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, 'PROCESSING', 0, 0, ?, ?)
                """, PAYMENT, ORDER, OWNER, new BigDecimal("250000.0000"), "VND",
                Timestamp.from(now.plusSeconds(300)), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                insert into payment_attempts (id, payment_id, attempt_number, status, provider,
                    provider_idempotency_key, created_at, updated_at)
                values (?, ?, 1, 'OPEN', 'STRIPE', ?, ?, ?)
                """, UUID.randomUUID(), PAYMENT, "provider-key-query", Timestamp.from(now), Timestamp.from(now));
    }

    @Test
    void ownerProjectionReturnsDurableFieldsAndAttemptCountOnly() {
        var adapter = new OwnedPaymentQueryJpaAdapter(payments, attempts);

        var result = adapter.load(new GetOwnedPaymentQuery(PAYMENT, OWNER)).orElseThrow();

        assertThat(result.amount()).isEqualByComparingTo("250000.0000");
        assertThat(result.currency()).isEqualTo("VND");
        assertThat(result.attemptsUsed()).isEqualTo(1);
        assertThat(result.status().name()).isEqualTo("PROCESSING");
    }

    @Test
    void wrongOwnerIsIndistinguishableFromMissingPaymentForBothKeys() {
        var adapter = new OwnedPaymentQueryJpaAdapter(payments, attempts);

        assertThat(adapter.load(new GetOwnedPaymentQuery(PAYMENT, OTHER_OWNER))).isEmpty();
        assertThat(adapter.loadByOrder(new GetOwnedPaymentByOrderQuery(ORDER, OTHER_OWNER))).isEmpty();
        assertThat(adapter.loadByOrder(new GetOwnedPaymentByOrderQuery(ORDER, OWNER))).isPresent();
    }
}
