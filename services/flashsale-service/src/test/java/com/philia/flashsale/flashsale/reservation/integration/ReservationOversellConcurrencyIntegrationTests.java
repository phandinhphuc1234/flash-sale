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
class ReservationOversellConcurrencyIntegrationTests {
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final Instant START = NOW.minusSeconds(60);
    private static final Instant END = NOW.plusSeconds(3600);
    private static final int ATTEMPTS = 1000;
    private static final int ALLOCATION = 100;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private ReserveCampaignQuotaService service;
    private UUID campaignId;
    private UUID variantId;

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
    void oneThousandConcurrentUniqueAttemptsCannotOversellOneHundredUnits() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(64);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ReservationDecisionResult>> futures = new ArrayList<>(ATTEMPTS);
        try {
            for (int index = 0; index < ATTEMPTS; index++) {
                UUID userId = UUID.randomUUID();
                int attempt = index;
                futures.add(executor.submit(() -> {
                    start.await();
                    return service.reserve(new ReserveCampaignQuotaCommand(campaignId, variantId, userId, 1,
                            "attempt-" + attempt, END));
                }));
            }
            start.countDown();

            long accepted = 0;
            for (Future<ReservationDecisionResult> future : futures) {
                ReservationDecisionResult result = future.get(30, TimeUnit.SECONDS);
                if (result.isAccepted()) {
                    accepted += result.snapshot().quantity();
                }
            }

            assertThat(accepted).isEqualTo(ALLOCATION);
            assertThat(redis.opsForHash().get(ReservationRedisKeys.stock(campaignId), variantId.toString()))
                    .isEqualTo("0");
            var userQuantityKeys = redis.keys("fs:{hot}:campaign:" + campaignId + ":user:*:qty");
            long userTotal = userQuantityKeys.stream()
                    .mapToLong(key -> Long.parseLong(String.valueOf(redis.opsForHash().get(key, variantId.toString()))))
                    .sum();
            assertThat(userTotal).isEqualTo(ALLOCATION);
            assertThat(userQuantityKeys).allSatisfy(key -> assertThat(
                    Long.parseLong(String.valueOf(redis.opsForHash().get(key, variantId.toString()))))
                    .isLessThanOrEqualTo(1));
            assertThat(redis.opsForStream().size(ReservationRedisKeys.handoff())).isEqualTo(ALLOCATION);
        } finally {
            executor.shutdownNow();
        }
    }

    private void projectActiveCampaign() {
        CampaignItemProjection item = new CampaignItemProjection(variantId, UUID.randomUUID(), "SKU-1",
                new BigDecimal("19.9900"), "VND", ALLOCATION, 1);
        CampaignSaleProjection projection = CampaignSaleProjection.recovered(campaignId, 1,
                CampaignProjectionState.SCHEDULED, START, END, item, NOW);
        CampaignProjectionRedisAdapter projectionAdapter = new CampaignProjectionRedisAdapter(redis);
        projectionAdapter.applyScheduled(projection);
        projectionAdapter.applyActivated(campaignId, 2, START, END, NOW);
    }
}
