package com.philia.flashsale.flashsale.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.flashsale.observability.FlashSaleObservability;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationFinalizationRedisAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationRedisKeys;
import com.philia.flashsale.flashsale.reservation.application.port.out.LoadReservationReconciliationPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationReconciliationCandidate;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationReconciliationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Proves durable finalization backlog replay repairs Redis exactly once after restart. */
@Testcontainers
class ReservationReconciliationIntegrationTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) connectionFactory.destroy();
    }

    @Test
    void confirmedReleasedAndExpiredRowsAreReconciledAndSafeAfterRestart() {
        UUID campaignId = UUID.randomUUID();
        ReservationReconciliationCandidate confirmed = candidate(campaignId,
                ReservationReconciliationCandidate.Status.CONFIRMED);
        ReservationReconciliationCandidate released = candidate(campaignId,
                ReservationReconciliationCandidate.Status.RELEASED);
        ReservationReconciliationCandidate expired = candidate(campaignId,
                ReservationReconciliationCandidate.Status.EXPIRED);
        seedReserved(confirmed);
        seedReleasedProjection(released);
        seedReleasedProjection(expired);

        InMemoryBacklog backlog = new InMemoryBacklog(List.of(confirmed, released, expired));
        var projection = new ReservationFinalizationRedisAdapter(redis, FlashSaleObservability.noop());
        var service = new ReservationReconciliationService(backlog, projection,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.reconcileBatch(100)).isEqualTo(3);
        assertThat(redis.opsForHash().get(ReservationRedisKeys.reservation(campaignId,
                confirmed.reservationId()), "status")).isEqualTo("CONFIRMED");
        assertThat(redis.opsForHash().get(ReservationRedisKeys.reservation(campaignId,
                released.reservationId()), "status")).isEqualTo("RELEASED");
        assertThat(redis.opsForHash().get(ReservationRedisKeys.reservation(campaignId,
                expired.reservationId()), "status")).isEqualTo("EXPIRED");
        assertThat(backlog.findPending(100)).isEmpty();

        // A restarted worker sees no pending marker and cannot apply a second semantic effect.
        var restarted = new ReservationReconciliationService(backlog, projection,
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
        assertThat(restarted.reconcileBatch(100)).isZero();
    }

    private ReservationReconciliationCandidate candidate(UUID campaignId,
            ReservationReconciliationCandidate.Status status) {
        return new ReservationReconciliationCandidate(UUID.randomUUID(), campaignId, UUID.randomUUID(),
                UUID.randomUUID(), 1, status);
    }

    private void seedReserved(ReservationReconciliationCandidate candidate) {
        redis.opsForHash().put(ReservationRedisKeys.reservation(candidate.campaignId(), candidate.reservationId()),
                "status", "RESERVED");
        redis.opsForZSet().add(ReservationRedisKeys.expirations(), candidate.reservationId().toString(),
                NOW.plusSeconds(300).toEpochMilli());
    }

    private void seedReleasedProjection(ReservationReconciliationCandidate candidate) {
        seedReserved(candidate);
        redis.opsForHash().put(ReservationRedisKeys.stock(candidate.campaignId()),
                candidate.variantId().toString(), "0");
        redis.opsForHash().put(ReservationRedisKeys.userQuantity(candidate.campaignId(), candidate.userId()),
                candidate.variantId().toString(), "1");
    }

    private static final class InMemoryBacklog implements LoadReservationReconciliationPort {
        private final List<ReservationReconciliationCandidate> pending;

        private InMemoryBacklog(List<ReservationReconciliationCandidate> candidates) {
            pending = new ArrayList<>(candidates);
        }

        @Override
        public List<ReservationReconciliationCandidate> findPending(int batchSize) {
            return pending.stream().limit(batchSize).toList();
        }

        @Override
        public void markReconciled(UUID reservationId, Instant at) {
            pending.removeIf(candidate -> candidate.reservationId().equals(reservationId));
        }
    }
}
