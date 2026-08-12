package com.philia.flashsale.flashsale.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.DurableAcceptanceJpaAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.FlashSaleReservationJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseEventOutboxJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseIdempotencyJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseRequestJpaRepository;
import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
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
class DurableAcceptancePersistenceIntegrationTests {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired DurableAcceptanceJpaAdapter acceptance;
    @Autowired PurchaseRequestJpaRepository requests;
    @Autowired FlashSaleReservationJpaRepository reservations;
    @Autowired PurchaseIdempotencyJpaRepository idempotency;
    @Autowired PurchaseEventOutboxJpaRepository outbox;
    @Autowired EntityManager entityManager;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.autoconfigure.exclude", () -> "");
    }

    @Test
    void commitsPurchaseReservationIdempotencyAndOutboxAsOneDurableResult() {
        AcceptedReservationSnapshot snapshot = snapshot();

        acceptance.persist(snapshot);
        entityManager.flush();
        acceptance.persist(snapshot);

        assertThat(requests.count()).isEqualTo(1);
        assertThat(reservations.count()).isEqualTo(1);
        assertThat(idempotency.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(outbox.findById(snapshot.eventId()).orElseThrow().getAggregateId())
                .isEqualTo(snapshot.purchaseRequestId());
        assertThat(outbox.findById(snapshot.eventId()).orElseThrow().getPayload().get("unitPrice"))
                .isEqualTo("19.9900");
    }

    @Test
    void rejectsChangedHashForTheSameScopedKeyWithoutWritingAnotherRow() {
        AcceptedReservationSnapshot snapshot = snapshot();
        acceptance.persist(snapshot);
        entityManager.flush();

        assertThatThrownBy(() -> acceptance.persist(copyWithRequestHash(snapshot,
                "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(requests.count()).isEqualTo(1);
    }

    @Test
    void writesOnlyAnExpiredTerminalTombstoneWhenExpiryAlreadyWon() {
        AcceptedReservationSnapshot snapshot = snapshotAt(NOW.minusSeconds(301));

        acceptance.persist(snapshot);
        entityManager.flush();

        assertThat(requests.findById(snapshot.purchaseRequestId()).orElseThrow().getOutcome().name())
                .isEqualTo("EXPIRED");
        assertThat(reservations.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    private static AcceptedReservationSnapshot snapshot() {
        return snapshotAt(NOW);
    }

    private static AcceptedReservationSnapshot snapshotAt(Instant acceptedAt) {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        return new AcceptedReservationSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-1",
                new BigDecimal("19.9900"), "VND", 1, hash, hash, acceptedAt, acceptedAt.plusSeconds(300),
                acceptedAt.plusSeconds(3600), "", "");
    }

    private static AcceptedReservationSnapshot copyWithRequestHash(AcceptedReservationSnapshot source,
            String requestHash) {
        return new AcceptedReservationSnapshot(source.purchaseRequestId(), source.reservationId(), source.eventId(),
                source.campaignId(), source.variantId(), source.userId(), source.inventoryAllocationId(),
                source.skuSnapshot(), source.unitPrice(), source.currency(), source.quantity(), requestHash,
                source.idempotencyKeyHash(), source.acceptedAt(), source.expiresAt(), source.retainedUntil(),
                source.traceparent(), source.tracestate());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AdapterConfiguration {
        @Bean
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        DurableAcceptanceJpaAdapter durableAcceptanceJpaAdapter(PurchaseRequestJpaRepository requests,
                FlashSaleReservationJpaRepository reservations, PurchaseIdempotencyJpaRepository idempotency,
                PurchaseEventOutboxJpaRepository outbox, Clock testClock, EntityManager entityManager) {
            return new DurableAcceptanceJpaAdapter(requests, reservations, idempotency, outbox, testClock,
                    entityManager);
        }
    }
}
