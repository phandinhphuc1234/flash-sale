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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AtomicReservationRedisIntegrationTests {
    private static final Instant NOW = Instant.parse("2030-08-10T12:00:00Z");
    private static final Instant START = NOW.minusSeconds(60);
    private static final Instant END = NOW.plusSeconds(60 * 60);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private AtomicReservationRedisAdapter adapter;
    private UUID campaignId;
    private UUID variantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        adapter = new AtomicReservationRedisAdapter(redis);
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
        campaignId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectActiveCampaign(10, 2);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void acceptsOneWinnerAndAtomicallyCreatesExpiryAndHandoffEntries() {
        ReservationDecisionResult result = service().reserve(command("same-key", 2));

        assertThat(result.outcome()).isEqualTo(ReservationDecisionResult.Outcome.ACCEPTED_NEW);
        assertThat(result.snapshot().unitPrice()).isEqualByComparingTo("19.9900");
        assertThat(result.snapshot().expiresAt()).isEqualTo(NOW.plusSeconds(5 * 60));
        assertThat(redis.opsForHash().get(ReservationRedisKeys.stock(campaignId), variantId.toString()))
                .isEqualTo("8");
        assertThat(redis.opsForHash().get(ReservationRedisKeys.userQuantity(campaignId, userId), variantId.toString()))
                .isEqualTo("2");
        assertThat(redis.opsForZSet().score(ReservationRedisKeys.expirations(),
                result.snapshot().reservationId().toString())).isEqualTo((double) NOW.plusSeconds(5 * 60).toEpochMilli());
        assertThat(redis.opsForStream().size(ReservationRedisKeys.handoff())).isEqualTo(1);
    }

    @Test
    void identicalReplayReturnsStableIdentityWithoutSecondMutationOrStreamEntry() {
        ReserveCampaignQuotaCommand command = command("same-key", 1);
        ReservationDecisionResult first = service().reserve(command);
        ReservationDecisionResult replay = service().reserve(command);

        assertThat(replay.outcome()).isEqualTo(ReservationDecisionResult.Outcome.ACCEPTED_REPLAY);
        assertThat(replay.snapshot().purchaseRequestId()).isEqualTo(first.snapshot().purchaseRequestId());
        assertThat(replay.snapshot().reservationId()).isEqualTo(first.snapshot().reservationId());
        assertThat(redis.opsForHash().get(ReservationRedisKeys.stock(campaignId), variantId.toString()))
                .isEqualTo("9");
        assertThat(redis.opsForStream().size(ReservationRedisKeys.handoff())).isEqualTo(1);
    }

    @Test
    void changedCanonicalRequestConflictsWithoutChangingCounters() {
        service().reserve(command("same-key", 1));
        ReservationDecisionResult conflict = service().reserve(command("same-key", 2));

        assertThat(conflict.outcome()).isEqualTo(ReservationDecisionResult.Outcome.IDEMPOTENCY_CONFLICT);
        assertThat(redis.opsForHash().get(ReservationRedisKeys.stock(campaignId), variantId.toString()))
                .isEqualTo("9");
        assertThat(redis.opsForStream().size(ReservationRedisKeys.handoff())).isEqualTo(1);
    }

    @Test
    void soldOutAndUserLimitRejectionsDoNotMutateQuota() {
        ReservationDecisionResult soldOut = service().reserve(command("too-many", 11));
        assertThat(soldOut.outcome()).isEqualTo(ReservationDecisionResult.Outcome.SOLD_OUT);
        assertThat(redis.opsForHash().get(ReservationRedisKeys.stock(campaignId), variantId.toString()))
                .isEqualTo("10");

        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
        projectActiveCampaign(10, 1);
        ReservationDecisionResult first = service().reserve(command("limit-one", 1));
        ReservationDecisionResult limit = service().reserve(command("limit-two", 1));
        assertThat(first.outcome()).isEqualTo(ReservationDecisionResult.Outcome.ACCEPTED_NEW);
        assertThat(limit.outcome()).isEqualTo(ReservationDecisionResult.Outcome.PURCHASE_LIMIT_EXCEEDED);
        assertThat(redis.opsForHash().get(ReservationRedisKeys.stock(campaignId), variantId.toString()))
                .isEqualTo("9");
    }

    @Test
    void returnsTypedUnknownLifecycleRecoveryAndVariantOutcomes() {
        flushRedis();
        assertThat(service().reserve(command("unknown", 1)).outcome())
                .isEqualTo(ReservationDecisionResult.Outcome.CAMPAIGN_UNKNOWN);

        projectScheduled(10, 2, START, END);
        assertThat(service().reserve(command("not-active", 1)).outcome())
                .isEqualTo(ReservationDecisionResult.Outcome.CAMPAIGN_NOT_ACTIVE);

        flushRedis();
        projectActive(10, 2, NOW.plusSeconds(60), NOW.plusSeconds(3600));
        assertThat(service().reserve(command("not-started", 1)).outcome())
                .isEqualTo(ReservationDecisionResult.Outcome.CAMPAIGN_NOT_STARTED);

        flushRedis();
        projectActive(10, 2, NOW.minusSeconds(3600), NOW.minusSeconds(1));
        assertThat(service().reserve(command("ended", 1)).outcome())
                .isEqualTo(ReservationDecisionResult.Outcome.CAMPAIGN_ENDED);

        flushRedis();
        new CampaignProjectionRedisAdapter(redis).applyActivated(campaignId, 2, START, END, NOW);
        assertThat(service().reserve(command("recovery", 1)).outcome())
                .isEqualTo(ReservationDecisionResult.Outcome.CAMPAIGN_RECOVERY_REQUIRED);

        flushRedis();
        projectActive(10, 2, START, END);
        UUID foreignVariant = UUID.randomUUID();
        ReserveCampaignQuotaCommand foreignVariantCommand = new ReserveCampaignQuotaCommand(campaignId,
                foreignVariant, userId, 1, "foreign-variant", END);
        assertThat(service().reserve(foreignVariantCommand).outcome())
                .isEqualTo(ReservationDecisionResult.Outcome.VARIANT_NOT_ELIGIBLE);
    }

    private ReserveCampaignQuotaService service() {
        return new ReserveCampaignQuotaService(adapter, new ReservationFingerprintService(),
                new ReservationIdentityFactory(), Clock.fixed(NOW, ZoneOffset.UTC), java.time.Duration.ofMinutes(5),
                java.time.Duration.ofHours(24));
    }

    private ReserveCampaignQuotaCommand command(String key, long quantity) {
        return new ReserveCampaignQuotaCommand(campaignId, variantId, userId, quantity, key, END);
    }

    private void projectActiveCampaign(long allocation, long perUserLimit) {
        projectActive(allocation, perUserLimit, START, END);
    }

    private void projectActive(long allocation, long perUserLimit, Instant start, Instant end) {
        CampaignProjectionRedisAdapter projectionAdapter = projectScheduled(allocation, perUserLimit, start, end);
        projectionAdapter.applyActivated(campaignId, 2, start, end, NOW);
    }

    private CampaignProjectionRedisAdapter projectScheduled(long allocation, long perUserLimit, Instant start,
            Instant end) {
        CampaignItemProjection item = new CampaignItemProjection(variantId, UUID.randomUUID(), "SKU-1",
                new BigDecimal("19.9900"), "VND", allocation, perUserLimit);
        CampaignSaleProjection projection = CampaignSaleProjection.recovered(campaignId, 1,
                CampaignProjectionState.SCHEDULED, start, end, item, NOW);
        CampaignProjectionRedisAdapter projectionAdapter = new CampaignProjectionRedisAdapter(redis);
        projectionAdapter.applyScheduled(projection);
        return projectionAdapter;
    }

    private void flushRedis() {
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }
}
