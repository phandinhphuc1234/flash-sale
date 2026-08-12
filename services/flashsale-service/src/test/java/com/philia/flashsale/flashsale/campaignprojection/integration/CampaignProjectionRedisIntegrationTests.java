package com.philia.flashsale.flashsale.campaignprojection.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.philia.flashsale.flashsale.campaignprojection.adapter.out.redis.CampaignProjectionRedisAdapter;
import com.philia.flashsale.flashsale.campaignprojection.adapter.out.redis.CampaignProjectionRedisKeys;
import com.philia.flashsale.flashsale.campaignprojection.application.result.CampaignProjectionUpdateResult;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignItemProjection;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignProjectionState;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignSaleProjection;
import java.math.BigDecimal;
import java.time.Instant;
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
class CampaignProjectionRedisIntegrationTests {
    private static final Instant START = Instant.parse("2026-08-10T12:00:00Z");
    private static final Instant END = Instant.parse("2026-08-10T13:00:00Z");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private CampaignProjectionRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        adapter = new CampaignProjectionRedisAdapter(redis);
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void duplicateAndStaleScheduledFactsDoNotResetQuota() {
        UUID campaignId = UUID.randomUUID();
        CampaignSaleProjection scheduled = projection(campaignId, 2, CampaignProjectionState.SCHEDULED, 100);

        assertEquals(CampaignProjectionUpdateResult.APPLIED, adapter.applyScheduled(scheduled));
        assertEquals("100", redis.opsForHash().get(CampaignProjectionRedisKeys.stock(campaignId),
                scheduled.item().variantId().toString()));
        String itemKey = CampaignProjectionRedisKeys.item(campaignId, scheduled.item().variantId());
        assertEquals("19.9900", redis.opsForHash().get(itemKey, "saleUnitPrice"));
        assertEquals("VND", redis.opsForHash().get(itemKey, "currency"));
        assertEquals("100", redis.opsForHash().get(itemKey, "allocatedQuantity"));
        assertEquals("2", redis.opsForHash().get(itemKey, "perUserLimit"));
        assertEquals(CampaignProjectionUpdateResult.NOOP_STALE, adapter.applyScheduled(scheduled));
        assertEquals(CampaignProjectionUpdateResult.NOOP_STALE,
                adapter.applyScheduled(projection(campaignId, 1, CampaignProjectionState.SCHEDULED, 1,
                        scheduled.item().variantId())));
        assertEquals("100", redis.opsForHash().get(CampaignProjectionRedisKeys.stock(campaignId),
                scheduled.item().variantId().toString()));
    }

    @Test
    void activationBeforeScheduleFailsClosedAndRecoveryCanRepairIt() {
        UUID campaignId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        assertEquals(CampaignProjectionUpdateResult.RECOVERY_REQUIRED,
                adapter.applyActivated(campaignId, 2, START, END, START));
        assertEquals("RECOVERY_REQUIRED", redis.opsForHash().get(
                CampaignProjectionRedisKeys.meta(campaignId), "state"));
        assertNull(redis.opsForHash().get(CampaignProjectionRedisKeys.stock(campaignId), variantId.toString()));

        CampaignSaleProjection recovered = projection(campaignId, 2, CampaignProjectionState.SCHEDULED, 100, variantId);
        assertEquals(CampaignProjectionUpdateResult.APPLIED, adapter.applyRecovered(recovered));
        assertEquals("100", redis.opsForHash().get(CampaignProjectionRedisKeys.stock(campaignId), variantId.toString()));
    }

    @Test
    void activationPreservesExistingQuotaAndWindowMismatchRequiresRecovery() {
        UUID campaignId = UUID.randomUUID();
        CampaignSaleProjection scheduled = projection(campaignId, 1, CampaignProjectionState.SCHEDULED, 100);
        assertEquals(CampaignProjectionUpdateResult.APPLIED, adapter.applyScheduled(scheduled));
        assertEquals(CampaignProjectionUpdateResult.APPLIED,
                adapter.applyActivated(campaignId, 2, START, END, START.plusSeconds(1)));
        assertEquals("ACTIVE", redis.opsForHash().get(CampaignProjectionRedisKeys.meta(campaignId), "state"));
        assertEquals("100", redis.opsForHash().get(CampaignProjectionRedisKeys.stock(campaignId),
                scheduled.item().variantId().toString()));

        UUID mismatchCampaign = UUID.randomUUID();
        CampaignSaleProjection mismatch = projection(mismatchCampaign, 1, CampaignProjectionState.SCHEDULED, 100);
        assertEquals(CampaignProjectionUpdateResult.APPLIED, adapter.applyScheduled(mismatch));
        assertEquals(CampaignProjectionUpdateResult.RECOVERY_REQUIRED,
                adapter.applyActivated(mismatchCampaign, 2, START.plusSeconds(1), END, START.plusSeconds(1)));
    }

    private CampaignSaleProjection projection(UUID campaignId, long version,
            CampaignProjectionState state, long quantity) {
        return projection(campaignId, version, state, quantity, UUID.randomUUID());
    }

    private CampaignSaleProjection projection(UUID campaignId, long version,
            CampaignProjectionState state, long quantity, UUID variantId) {
        CampaignItemProjection item = new CampaignItemProjection(variantId, UUID.randomUUID(), "SKU-1",
                new BigDecimal("19.9900"), "VND", quantity, 2);
        return CampaignSaleProjection.recovered(campaignId, version, state, START, END, item, START);
    }
}
