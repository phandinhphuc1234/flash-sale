package com.philia.flashsale.flashsale.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.DurableAcceptanceJpaAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.FlashSaleReservationJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseEventOutboxJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseIdempotencyJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseRequestJpaRepository;
import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {"spring.liquibase.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(LiquibaseAutoConfiguration.class)
@Import(DurableAcceptancePersistenceIntegrationTests.AdapterConfiguration.class)
@Testcontainers
class DurableAcceptanceConcurrencyIntegrationTests {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired DurableAcceptanceJpaAdapter acceptance;
    @Autowired PurchaseRequestJpaRepository requests;
    @Autowired FlashSaleReservationJpaRepository reservations;
    @Autowired PurchaseIdempotencyJpaRepository idempotency;
    @Autowired PurchaseEventOutboxJpaRepository outbox;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) { registry.add("spring.autoconfigure.exclude", () -> ""); }

    @Test
    void concurrentRequestAndWorkerAttemptsCommitOneStableAcceptedOutcome() throws Exception {
        AcceptedReservationSnapshot snapshot = snapshot();
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Throwable>> attempts = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                attempts.add(() -> {
                    try { acceptance.persist(snapshot); return null; }
                    catch (Throwable failure) { return failure; }
                });
            }
            assertThat(workers.invokeAll(attempts).stream().map(future -> {
                try { return future.get(); } catch (Exception exception) { return exception; }
            }).filter(java.util.Objects::nonNull).toList()).isEmpty();
        } finally {
            workers.shutdownNow();
        }
        assertThat(requests.count()).isEqualTo(1);
        assertThat(reservations.count()).isEqualTo(1);
        assertThat(idempotency.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
    }

    private static AcceptedReservationSnapshot snapshot() {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        return new AcceptedReservationSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-1",
                new BigDecimal("19.9900"), "VND", 1, hash, hash, NOW, NOW.plusSeconds(300),
                NOW.plusSeconds(3600), "", "");
    }
}
