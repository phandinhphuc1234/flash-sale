package com.philia.flashsale.campaign.campaign.adapter.in.scheduling;

import com.philia.flashsale.campaign.campaign.application.command.ActivateCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.command.EndCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.port.in.ActivateCampaignUseCase;
import com.philia.flashsale.campaign.campaign.application.port.in.EndCampaignUseCase;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadDueCampaignsPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignClockPort;
import com.philia.flashsale.campaign.websupport.context.CampaignRequestContext;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Drives due activation and ending without owning any lifecycle business rule. */
@Component
public final class CampaignLifecycleScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignLifecycleScheduler.class);
    private static final String SYSTEM_ACTOR = "campaign-lifecycle-scheduler";

    private final LoadDueCampaignsPort dueCampaigns;
    private final ActivateCampaignUseCase activateCampaign;
    private final EndCampaignUseCase endCampaign;
    private final CampaignClockPort clock;
    private final int batchSize;

    public CampaignLifecycleScheduler(
            LoadDueCampaignsPort dueCampaigns,
            ActivateCampaignUseCase activateCampaign,
            EndCampaignUseCase endCampaign,
            CampaignClockPort clock,
            @Value("${flashsale.campaign.lifecycle.batch-size:100}")
            int batchSize) {
        this.dueCampaigns = dueCampaigns;
        this.activateCampaign = activateCampaign;
        this.endCampaign = endCampaign;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${flashsale.campaign.lifecycle.scan-delay:2s}")
    public void processDueCampaigns() {
        Instant now = clock.now();
        String traceId = CampaignRequestContext.normalizeOrGenerate(null);
        dueCampaigns.findDueForActivation(now, batchSize).forEach(campaign -> {
            try {
                activateCampaign.activate(new ActivateCampaignCommand(
                        campaign.id(), campaign.version(), SYSTEM_ACTOR, now, traceId, false));
            } catch (RuntimeException exception) {
                LOG.warn("campaign_activation_failed campaignId={} traceId={} exceptionType={}",
                        campaign.id(), traceId, exception.getClass().getSimpleName());
            }
        });
        dueCampaigns.findDueForEnding(now, batchSize).forEach(campaign -> {
            try {
                endCampaign.end(new EndCampaignCommand(
                        campaign.id(), campaign.version(), SYSTEM_ACTOR, now, traceId));
            } catch (RuntimeException exception) {
                LOG.warn("campaign_ending_failed campaignId={} traceId={} exceptionType={}",
                        campaign.id(), traceId, exception.getClass().getSimpleName());
            }
        });
    }
}
