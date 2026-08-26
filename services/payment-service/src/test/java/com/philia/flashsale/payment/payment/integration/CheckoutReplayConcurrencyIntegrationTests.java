package com.philia.flashsale.payment.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentAttemptJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentClientIdempotencyJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentJpaRepository;
import com.philia.flashsale.payment.payment.application.exception.PaymentCheckoutException;
import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestCommand;
import com.philia.flashsale.payment.payment.application.model.StartCheckoutCommand;
import com.philia.flashsale.payment.payment.application.model.StartCheckoutResult;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutCreateRequest;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutExpireRequest;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutResult;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutRetrieveRequest;
import com.philia.flashsale.payment.payment.application.port.in.AcceptPaymentRequestUseCase;
import com.philia.flashsale.payment.payment.application.port.in.StartCheckoutUseCase;
import com.philia.flashsale.payment.payment.application.port.out.HostedCheckoutProviderPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

/** PostgreSQL regression proof for Checkout lock ordering and concurrent identity convergence. */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "payment.acceptance.enabled=true",
        "payment.checkout.enabled=true",
        "payment.stripe.enabled=true",
        "payment.stripe.secret-key=sk_test_concurrency_fixture",
        "payment.stripe.publishable-key=pk_test_concurrency_fixture",
        "payment.stripe.webhook-secret=whsec_concurrency_fixture",
        "payment.kafka.consumer-enabled=false",
        "payment.kafka.outbox-publisher-enabled=false",
        "payment.webhook.processing-enabled=false",
        "payment.recovery.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=32",
        "spring.datasource.hikari.connection-timeout=30000",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class CheckoutReplayConcurrencyIntegrationTests {

    private static final UUID OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db").withUsername("flashsale").withPassword("test-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AcceptPaymentRequestUseCase acceptance;
    @Autowired StartCheckoutUseCase checkout;
    @Autowired PaymentJpaRepository payments;
    @Autowired PaymentAttemptJpaRepository attempts;
    @Autowired PaymentClientIdempotencyJpaRepository idempotency;
    @Autowired DeterministicProvider provider;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("truncate payment_outbox_events, payment_recovery_work, "
                + "payment_provider_event_receipts, payment_client_idempotency, "
                + "payment_command_inbox, payment_attempts, payments cascade");
        provider.reset();
    }

    @Test
    void concurrentSameKeyReplaysUseOneLockOrderWithoutDeadlock() throws Exception {
        UUID paymentId = acceptPayment();
        String key = "same-key-replay";
        checkout.start(new StartCheckoutCommand(paymentId, OWNER, key));

        List<StartCheckoutResult> results = runConcurrently(20,
                index -> checkout.start(new StartCheckoutCommand(paymentId, OWNER, key)));

        assertThat(results).hasSize(20)
                .allMatch(result -> result.outcome() == StartCheckoutResult.Outcome.REPLAYED);
        assertThat(attempts.count()).isOne();
        assertThat(idempotency.count()).isOne();
        assertThat(provider.createCalls.get()).isOne();
        assertThat(provider.retrieveCalls.get()).isEqualTo(20);
    }

    @Test
    void oneHundredDifferentKeysProduceOnlyOneActiveProviderWorkflow() throws Exception {
        UUID paymentId = acceptPayment();
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger inProgress = new AtomicInteger();

        runConcurrently(100, index -> {
            try {
                checkout.start(new StartCheckoutCommand(paymentId, OWNER, "different-key-" + index));
                accepted.incrementAndGet();
            } catch (PaymentCheckoutException exception) {
                assertThat(exception.outcome())
                        .isEqualTo(PaymentCheckoutException.Outcome.CHECKOUT_IN_PROGRESS);
                inProgress.incrementAndGet();
            }
            return Boolean.TRUE;
        });

        UUID persistedPaymentId = payments.findById(paymentId).orElseThrow().getId();
        assertThat(persistedPaymentId).isEqualTo(paymentId);
        assertThat(accepted.get()).isOne();
        assertThat(inProgress.get()).isEqualTo(99);
        assertThat(attempts.count()).isOne();
        assertThat(provider.createCalls.get()).isOne();
    }

    @Test
    void serviceLocalCheckoutReplayP95StaysBelowOneHundredFiftyMilliseconds() {
        UUID paymentId = acceptPayment();
        String key = "service-local-latency";
        checkout.start(new StartCheckoutCommand(paymentId, OWNER, key));

        // Warm the JPA/Hikari/JIT path before measuring.  The latency budget is
        // for a steady-state replay, not the first call after container and
        // application startup; including cold-start work makes this regression
        // test depend on the load of unrelated modules in the full reactor.
        for (int index = 0; index < 25; index++) {
            StartCheckoutResult warmup = checkout.start(new StartCheckoutCommand(paymentId, OWNER, key));
            assertThat(warmup.outcome()).isEqualTo(StartCheckoutResult.Outcome.REPLAYED);
        }

        List<Long> durations = new ArrayList<>();

        for (int index = 0; index < 200; index++) {
            long started = System.nanoTime();
            StartCheckoutResult result = checkout.start(new StartCheckoutCommand(paymentId, OWNER, key));
            durations.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            assertThat(result.outcome()).isEqualTo(StartCheckoutResult.Outcome.REPLAYED);
        }

        Collections.sort(durations);
        long p95Millis = durations.get((int) Math.ceil(durations.size() * 0.95) - 1);
        System.out.printf("PAYMENT_CHECKOUT_LOCAL_P95_MS=%d%n", p95Millis);
        assertThat(p95Millis).isLessThan(150L);
    }

    private UUID acceptPayment() {
        Instant now = Instant.now();
        UUID orderId = UUID.randomUUID();
        return acceptance.accept(new AcceptPaymentRequestCommand(
                UUID.randomUUID(), "PaymentRequested", 1, "order-service", "ORDER", orderId, 1,
                orderId, UUID.randomUUID(), now, orderId, OWNER, new BigDecimal("100000.0000"),
                "VND", now.plusSeconds(3600),
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01", null)).paymentId();
    }

    private <T> List<T> runConcurrently(int count, IndexedWork<T> work) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<T>>();
            for (int index = 0; index < count; index++) {
                int item = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return work.run(item);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @FunctionalInterface
    interface IndexedWork<T> {
        T run(int index) throws Exception;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderConfiguration {
        @Bean
        @Primary
        DeterministicProvider deterministicProvider() {
            return new DeterministicProvider();
        }
    }

    static final class DeterministicProvider implements HostedCheckoutProviderPort {
        final AtomicInteger createCalls = new AtomicInteger();
        final AtomicInteger retrieveCalls = new AtomicInteger();

        @Override
        public HostedCheckoutResult create(HostedCheckoutCreateRequest request) {
            createCalls.incrementAndGet();
            return HostedCheckoutResult.open("cs_test_concurrency", "https://checkout.test/concurrency",
                    Instant.now().plusSeconds(1800), Instant.now());
        }

        @Override
        public HostedCheckoutResult retrieve(HostedCheckoutRetrieveRequest request) {
            retrieveCalls.incrementAndGet();
            return HostedCheckoutResult.open(request.providerSessionId(), "https://checkout.test/concurrency",
                    Instant.now().plusSeconds(1800), Instant.now());
        }

        @Override
        public HostedCheckoutResult expire(HostedCheckoutExpireRequest request) {
            return retrieve(new HostedCheckoutRetrieveRequest(request.providerSessionId()));
        }

        void reset() {
            createCalls.set(0);
            retrieveCalls.set(0);
        }
    }
}
