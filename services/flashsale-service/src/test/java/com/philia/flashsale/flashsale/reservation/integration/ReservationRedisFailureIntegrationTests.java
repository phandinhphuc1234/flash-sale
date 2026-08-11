package com.philia.flashsale.flashsale.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.flashsale.campaignprojection.adapter.out.redis.CampaignProjectionRedisAdapter;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignItemProjection;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignProjectionState;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignSaleProjection;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.AtomicReservationRedisAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationRedisKeys;
import com.philia.flashsale.flashsale.reservation.application.command.ReserveCampaignQuotaCommand;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationFingerprintService;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationIdentityFactory;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReserveCampaignQuotaService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
class ReservationRedisFailureIntegrationTests {
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final Instant START = NOW.minusSeconds(60);
    private static final Instant END = NOW.plusSeconds(3600);

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
    @Order(1)
    void scriptExecutorReloadsApprovedLuaAfterRedisScriptCacheIsFlushed() {
        ReserveCampaignQuotaCommand command = command("reload-key");
        assertThat(service.reserve(command).isAccepted()).isTrue();
        try (var connection = connectionFactory.getConnection()) {
            connection.scriptingCommands().scriptFlush();
        }

        assertThat(service.reserve(command).outcome())
                .isEqualTo(com.philia.flashsale.flashsale.reservation.application.result.ReservationDecisionResult.Outcome.ACCEPTED_REPLAY);
        assertThat(redis.opsForStream().size(ReservationRedisKeys.handoff())).isEqualTo(1);
    }

    @Test
    @Order(2)
    void businessKeysUseHotHashTagAndNeverContainTheRawIdempotencyKey() {
        String rawKey = "raw-secret-key";
        service.reserve(command(rawKey));

        assertThat(redis.keys("fs:{hot}:campaign:*")).allSatisfy(key -> assertThat(key).contains("{hot}"));
        assertThat(redis.keys("*" + rawKey + "*")).isEmpty();
    }

    @Test
    @Order(3)
    void redisUnavailableFailsClosedWithoutAJavaFallback() {
        REDIS.stop();

        assertThatThrownBy(() -> service.reserve(command("redis-down")))
                .isInstanceOf(RuntimeException.class);
    }

    private ReserveCampaignQuotaCommand command(String idempotencyKey) {
        return new ReserveCampaignQuotaCommand(campaignId, variantId, userId, 1, idempotencyKey, END);
    }

    private void projectActiveCampaign() {
        CampaignItemProjection item = new CampaignItemProjection(variantId, UUID.randomUUID(), "SKU-1",
                new BigDecimal("19.9900"), "VND", 10, 2);
        CampaignSaleProjection projection = CampaignSaleProjection.recovered(campaignId, 1,
                CampaignProjectionState.SCHEDULED, START, END, item, NOW);
        CampaignProjectionRedisAdapter projectionAdapter = new CampaignProjectionRedisAdapter(redis);
        projectionAdapter.applyScheduled(projection);
        projectionAdapter.applyActivated(campaignId, 2, START, END, NOW);
    }
}
