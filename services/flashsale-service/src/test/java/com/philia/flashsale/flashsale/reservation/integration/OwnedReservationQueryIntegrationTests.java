package com.philia.flashsale.flashsale.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.DurableAcceptanceJpaAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.OwnedReservationQueryJpaAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.FlashSaleReservationJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseEventOutboxJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseIdempotencyJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseRequestJpaRepository;
import com.philia.flashsale.flashsale.reservation.application.port.in.GetOwnedReservationUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.out.LoadOwnedReservationPort;
import com.philia.flashsale.flashsale.reservation.application.query.GetOwnedReservationQuery;
import com.philia.flashsale.flashsale.reservation.application.usecase.GetOwnedReservationService;
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
@Import(OwnedReservationQueryIntegrationTests.AdapterConfiguration.class)
@Testcontainers
class OwnedReservationQueryIntegrationTests {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DurableAcceptanceJpaAdapter acceptance;

    @Autowired
    private GetOwnedReservationUseCase query;

    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.autoconfigure.exclude", () -> "");
    }

    @Test
    void loadsOnlyTheDurableReservationOwnedByTheAuthenticatedShopper() {
        UUID ownerId = UUID.randomUUID();
        AcceptedReservationSnapshot snapshot = snapshot(ownerId);
        acceptance.persist(snapshot);
        entityManager.flush();
        entityManager.clear();

        var owned = query.getOwnedReservation(new GetOwnedReservationQuery(snapshot.reservationId(), ownerId));
        var foreign = query.getOwnedReservation(
                new GetOwnedReservationQuery(snapshot.reservationId(), UUID.randomUUID()));
        var unknown = query.getOwnedReservation(new GetOwnedReservationQuery(UUID.randomUUID(), ownerId));

        assertThat(owned).isPresent();
        assertThat(owned.orElseThrow().purchaseRequestId()).isEqualTo(snapshot.purchaseRequestId());
        assertThat(owned.orElseThrow().unitPrice()).isEqualByComparingTo("19.9900");
        assertThat(owned.orElseThrow().currency()).isEqualTo("VND");
        assertThat(owned.orElseThrow().acceptedAt()).isEqualTo(NOW);
        assertThat(owned.orElseThrow().expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(foreign).isEmpty();
        assertThat(unknown).isEmpty();
    }

    private static AcceptedReservationSnapshot snapshot(UUID userId) {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        return new AcceptedReservationSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), userId, UUID.randomUUID(), "SKU-1",
                new BigDecimal("19.9900"), "VND", 1, hash, hash, NOW, NOW.plusSeconds(300),
                NOW.plusSeconds(3600), "", "");
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

        @Bean
        LoadOwnedReservationPort loadOwnedReservationPort(FlashSaleReservationJpaRepository reservations) {
            return new OwnedReservationQueryJpaAdapter(reservations);
        }

        @Bean
        GetOwnedReservationUseCase getOwnedReservationUseCase(LoadOwnedReservationPort reservations) {
            return new GetOwnedReservationService(reservations);
        }
    }
}
