package com.philia.flashsale.flashsale.campaignprojection.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.philia.flashsale.flashsale.campaignprojection.application.command.ApplyCampaignActivatedCommand;
import com.philia.flashsale.flashsale.campaignprojection.application.command.ApplyCampaignScheduledCommand;
import com.philia.flashsale.flashsale.campaignprojection.application.port.out.QueueCampaignRecoveryPort;
import com.philia.flashsale.flashsale.campaignprojection.application.port.out.StoreCampaignProjectionPort;
import com.philia.flashsale.flashsale.campaignprojection.application.result.CampaignProjectionUpdateResult;
import com.philia.flashsale.flashsale.campaignprojection.application.usecase.CampaignProjectionService;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignItemProjection;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignSaleProjection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CampaignProjectionServiceTests {
    private static final UUID CAMPAIGN_ID = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-08-10T12:00:00Z");
    private static final Instant END = Instant.parse("2026-08-10T13:00:00Z");

    @Test
    void activationRecoveryIsQueuedWhenRedisReportsMissingProjection() {
        AtomicReference<UUID> queued = new AtomicReference<>();
        StoreCampaignProjectionPort store = new StoreCampaignProjectionPort() {
            @Override
            public CampaignProjectionUpdateResult applyScheduled(CampaignSaleProjection projection) {
                return CampaignProjectionUpdateResult.APPLIED;
            }

            @Override
            public CampaignProjectionUpdateResult applyActivated(UUID campaignId, long version,
                    Instant startsAt, Instant endsAt, Instant updatedAt) {
                return CampaignProjectionUpdateResult.RECOVERY_REQUIRED;
            }

            @Override
            public CampaignProjectionUpdateResult applyRecovered(CampaignSaleProjection projection) {
                return CampaignProjectionUpdateResult.APPLIED;
            }
        };
        QueueCampaignRecoveryPort queue = (campaignId, nextAttemptAt) -> queued.set(campaignId);
        var service = new CampaignProjectionService(store, queue);

        CampaignProjectionUpdateResult result = service.project(new ApplyCampaignActivatedCommand(
                CAMPAIGN_ID, 2, START, END, START));

        assertEquals(CampaignProjectionUpdateResult.RECOVERY_REQUIRED, result);
        assertEquals(CAMPAIGN_ID, queued.get());
    }

    @Test
    void scheduledCommandIsMappedToACompleteProjectionBeforeThePort() {
        AtomicReference<CampaignSaleProjection> captured = new AtomicReference<>();
        StoreCampaignProjectionPort store = new StoreCampaignProjectionPort() {
            @Override
            public CampaignProjectionUpdateResult applyScheduled(CampaignSaleProjection projection) {
                captured.set(projection);
                return CampaignProjectionUpdateResult.APPLIED;
            }

            @Override
            public CampaignProjectionUpdateResult applyActivated(UUID campaignId, long version,
                    Instant startsAt, Instant endsAt, Instant updatedAt) {
                return CampaignProjectionUpdateResult.NOOP_STALE;
            }

            @Override
            public CampaignProjectionUpdateResult applyRecovered(CampaignSaleProjection projection) {
                return CampaignProjectionUpdateResult.NOOP_STALE;
            }
        };
        var service = new CampaignProjectionService(store, (id, at) -> { });
        var item = new CampaignItemProjection(UUID.randomUUID(), UUID.randomUUID(), "SKU-1",
                new BigDecimal("10.0000"), "VND", 10, 1);

        assertEquals(CampaignProjectionUpdateResult.APPLIED, service.project(
                new ApplyCampaignScheduledCommand(CAMPAIGN_ID, 1, START, END, item, START)));
        assertTrue(captured.get().hasSameWindow(START, END));
        assertEquals(item, captured.get().item());
    }
}
