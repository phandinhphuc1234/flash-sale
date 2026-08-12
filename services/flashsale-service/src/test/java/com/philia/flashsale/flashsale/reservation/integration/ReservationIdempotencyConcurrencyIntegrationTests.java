package com.philia.flashsale.flashsale.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.flashsale.campaignprojection.adapter.out.redis.CampaignProjectionRedisAdapter;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignItemProjection;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignProjectionState;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignSaleProjection;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.AtomicReservationRedisAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationRedisKeys;
import com.philia.flashsale.flashsale.reservation.application.command.ReserveCampaignQuotaCommand;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationDecisionResult;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationFingerprintService;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationIdentityFactory;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReserveCampaignQuotaService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ReservationIdempotencyConcurrencyIntegrationTests {
    private static final Instant NOW = Instant.parse("2030-08-10T12:00:00Z");
    private static final Instant START = NOW.minusSeconds(60);
    private static final Instant END = NOW.plusSeconds(3600);
    private static final int RETRIES = 100;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private ReserveCampaignQuotaService service;
    private UUID campaignId;
    private UUID variantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
        campaignId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectActiveCampaign();
        service = new ReserveCampaignQuotaService(new AtomicReservationRedisAdapter(redis),
                new ReservationFingerprintService(), new ReservationIdentityFactory(),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5), Duration.ofHours(24));
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void oneHundredConcurrentIdenticalRetriesCreateOneWinnerAndOneStreamEntry() throws Exception {
        ReserveCampaignQuotaCommand command = new ReserveCampaignQuotaCommand(campaignId, variantId, userId, 1,
                "same-key", END);
        List<ReservationDecisionResult> results = runConcurrently(command);

        assertThat(results).hasSize(RETRIES);
        assertThat(results).allMatch(ReservationDecisionResult::isAccepted);
        assertThat(results.stream().filter(result -> result.outcome()
                == ReservationDecisionResult.Outcome.ACCEPTED_NEW)).hasSize(1);
        assertThat(results.stream().map(result -> result.snapshot().reservationId()).distinct()).hasSize(1);
        assertThat(results.stream().map(result -> result.snapshot().purchaseRequestId()).distinct()).hasSize(1);
        assertThat(results.stream().map(result -> result.snapshot().eventId()).distinct()).hasSize(1);
        assertThat(redis.opsForHash().get(ReservationRedisKeys.stock(campaignId), variantId.toString()))
                .isEqualTo("9");
        assertThat(redis.opsForStream().size(ReservationRedisKeys.handoff())).isEqualTo(1);

        ReservationDecisionResult conflict = service.reserve(new ReserveCampaignQuotaCommand(campaignId, variantId,
                userId, 2, "same-key", END));
        assertThat(conflict.outcome()).isEqualTo(ReservationDecisionResult.Outcome.IDEMPOTENCY_CONFLICT);
        assertThat(redis.opsForHash().get(ReservationRedisKeys.stock(campaignId), variantId.toString()))
                .isEqualTo("9");
    }

    private List<ReservationDecisionResult> runConcurrently(ReserveCampaignQuotaCommand command) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ReservationDecisionResult>> futures = new ArrayList<>(RETRIES);
        try {
            for (int index = 0; index < RETRIES; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return service.reserve(command);
                }));
            }
            start.countDown();
            List<ReservationDecisionResult> results = new ArrayList<>(RETRIES);
            for (Future<ReservationDecisionResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private void projectActiveCampaign() {
        CampaignItemProjection item = new CampaignItemProjection(variantId, UUID.randomUUID(), "SKU-1",
                new BigDecimal("19.9900"), "VND", 10, 1);
        CampaignSaleProjection projection = CampaignSaleProjection.recovered(campaignId, 1,
                CampaignProjectionState.SCHEDULED, START, END, item, NOW);
        CampaignProjectionRedisAdapter projectionAdapter = new CampaignProjectionRedisAdapter(redis);
        projectionAdapter.applyScheduled(projection);
        projectionAdapter.applyActivated(campaignId, 2, START, END, NOW);
    }
}
