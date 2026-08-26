package com.philia.flashsale.payment.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestCommand;
import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestResult;
import com.philia.flashsale.payment.payment.application.port.in.AcceptPaymentRequestUseCase;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentCommandInboxJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 100-delivery PostgreSQL proof for command convergence and contradictory races. */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "payment.kafka.consumer-enabled=false",
        "payment.kafka.outbox-publisher-enabled=false",
        "payment.acceptance.enabled=true",
        "payment.recovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        // The test intentionally starts 100 deliveries. Keep a bounded pool,
        // but allow queued transactions to wait instead of failing at 2 seconds.
        "spring.datasource.hikari.maximum-pool-size=32",
        "spring.datasource.hikari.connection-timeout=30000"
})
class PaymentCommandConcurrencyIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-08-17T10:00:00Z");
    private static final UUID ORDER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID USER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db")
            .withUsername("flashsale")
            .withPassword("test-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AcceptPaymentRequestUseCase acceptance;

    @Autowired
    private PaymentJpaRepository payments;

    @Autowired
    private PaymentCommandInboxJpaRepository inbox;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("truncate payment_outbox_events, payment_recovery_work, payment_provider_event_receipts, "
                + "payment_client_idempotency, payment_command_inbox, payment_attempts, payments cascade");
    }

    @Test
    void oneHundredConcurrentCopiesOfTheSameEventCreateOnePayment() throws Exception {
        UUID eventId = UUID.randomUUID();
        List<AcceptPaymentRequestResult> results = invokeConcurrently(index -> command(eventId,
                new BigDecimal("125.0000")));

        assertThat(results).hasSize(100);
        assertThat(results).allMatch(result -> result.outcome() == AcceptPaymentRequestResult.Outcome.ACCEPTED
                || result.outcome() == AcceptPaymentRequestResult.Outcome.EVENT_REPLAYED);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(inbox.count()).isEqualTo(1);
    }

    @Test
    void oneHundredEquivalentDifferentEventIdsConvergeToOnePayment() throws Exception {
        List<AcceptPaymentRequestResult> results = invokeConcurrently(index -> command(UUID.randomUUID(),
                new BigDecimal("125.00")));

        assertThat(results).hasSize(100);
        assertThat(results).anyMatch(result -> result.outcome() == AcceptPaymentRequestResult.Outcome.ACCEPTED);
        assertThat(results).allMatch(result -> result.outcome() == AcceptPaymentRequestResult.Outcome.ACCEPTED
                || result.outcome() == AcceptPaymentRequestResult.Outcome.BUSINESS_REPLAYED);
        assertThat(results.stream().map(AcceptPaymentRequestResult::paymentId).distinct()).hasSize(1);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(inbox.count()).isEqualTo(1);
    }

    @Test
    void contradictoryConcurrentCommandsNeverMutateTheWinningSnapshot() throws Exception {
        List<AcceptPaymentRequestResult> results = invokeConcurrently(index -> command(UUID.randomUUID(),
                index % 2 == 0 ? new BigDecimal("125") : new BigDecimal("250")));

        assertThat(results).hasSize(100);
        assertThat(results).allMatch(result -> result.outcome() == AcceptPaymentRequestResult.Outcome.ACCEPTED
                || result.outcome() == AcceptPaymentRequestResult.Outcome.BUSINESS_REPLAYED
                || result.outcome() == AcceptPaymentRequestResult.Outcome.CONFLICT);
        assertThat(results.stream().map(AcceptPaymentRequestResult::paymentId).distinct()).hasSize(1);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(inbox.count()).isEqualTo(1);
        assertThat(payments.findAll().get(0).getAmount()).satisfiesAnyOf(
                amount -> assertThat(amount).isEqualByComparingTo("125"),
                amount -> assertThat(amount).isEqualByComparingTo("250"));
    }

    private List<AcceptPaymentRequestResult> invokeConcurrently(
            java.util.function.IntFunction<AcceptPaymentRequestCommand> commandFactory) throws Exception {
        int count = 100;
        // Every delivery must reach the barrier before any request starts. Use one
        // worker per delivery so the barrier cannot deadlock behind a smaller pool;
        // PostgreSQL/advisory-lock contention is the behavior under test.
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<AcceptPaymentRequestResult>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                int taskIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("concurrency test did not start");
                    }
                    return acceptance.accept(commandFactory.apply(taskIndex));
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<AcceptPaymentRequestResult> results = new ArrayList<>();
            for (Future<AcceptPaymentRequestResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private AcceptPaymentRequestCommand command(UUID eventId, BigDecimal amount) {
        return new AcceptPaymentRequestCommand(eventId, "PaymentRequested", 1, "order-service", "ORDER",
                ORDER_ID, 1, UUID.randomUUID(), UUID.randomUUID(), NOW, ORDER_ID, USER_ID, amount, "VND",
                NOW.plusSeconds(600), null, null);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PaymentAcceptanceTestConfiguration {

        @Bean
        @Primary
        PaymentClockPort fixedPaymentClock() {
            return () -> NOW;
        }
    }
}
