package com.philia.flashsale.flashsale.campaignprojection.adapter.in.scheduling;

import com.philia.flashsale.flashsale.campaignprojection.application.command.RecoverCampaignProjectionCommand;
import com.philia.flashsale.flashsale.campaignprojection.application.port.in.RecoverCampaignProjectionUseCase;
import com.philia.flashsale.flashsale.campaignprojection.application.port.out.LoadDueCampaignRecoveryPort;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs control-plane snapshot recovery outside the shopper reservation request
 * path.
 */
@Component
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public final class CampaignProjectionRecoveryJob {
    private static final Logger LOG = LoggerFactory.getLogger(CampaignProjectionRecoveryJob.class);

    private final LoadDueCampaignRecoveryPort dueRecoveries;
    private final RecoverCampaignProjectionUseCase recoveryUseCase;
    private final Clock clock;

    @Autowired
    public CampaignProjectionRecoveryJob(LoadDueCampaignRecoveryPort dueRecoveries,
            RecoverCampaignProjectionUseCase recoveryUseCase) {
        this(dueRecoveries, recoveryUseCase, Clock.systemUTC());
    }

    CampaignProjectionRecoveryJob(LoadDueCampaignRecoveryPort dueRecoveries,
            RecoverCampaignProjectionUseCase recoveryUseCase, Clock clock) {
        this.dueRecoveries = dueRecoveries;
        this.recoveryUseCase = recoveryUseCase;
        this.clock = clock;
    }

    // Scheduled method that runs at a fixed interval to recover due Campaign
    // projections.
    // It loads due recoveries from Redis and invokes the recovery use case for each
    // campaign ID. If a Redis outage occurs,
    // it logs a warning and leaves the queue untouched for the next scheduled scan.
    @Scheduled(fixedDelayString = "${flashsale.campaign-projection.recovery-interval}")
    public void recoverDueCampaigns() {
        Instant now = clock.instant();
        try {
            for (var campaignId : dueRecoveries.loadDue(now)) {
                recoveryUseCase.recover(new RecoverCampaignProjectionCommand(campaignId, now));
            }
        } catch (RuntimeException exception) {
            // A Redis outage must leave the queue untouched for the next scheduled scan.
            LOG.warn("flashsale_campaign_projection_recovery_scan_failed exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
