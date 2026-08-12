package com.philia.flashsale.flashsale.campaignprojection.application.usecase;

import com.philia.flashsale.flashsale.campaignprojection.application.command.ApplyCampaignActivatedCommand;
import com.philia.flashsale.flashsale.campaignprojection.application.command.ApplyCampaignScheduledCommand;
import com.philia.flashsale.flashsale.campaignprojection.application.port.in.ProjectCampaignActivationUseCase;
import com.philia.flashsale.flashsale.campaignprojection.application.port.in.ProjectCampaignScheduleUseCase;
import com.philia.flashsale.flashsale.campaignprojection.application.port.out.QueueCampaignRecoveryPort;
import com.philia.flashsale.flashsale.campaignprojection.application.port.out.StoreCampaignProjectionPort;
import com.philia.flashsale.flashsale.campaignprojection.application.result.CampaignProjectionUpdateResult;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignSaleProjection;
import java.util.Objects;

/** Orchestrates Campaign lifecycle facts without depending on Kafka or Redis APIs. */
public final class CampaignProjectionService
        implements ProjectCampaignScheduleUseCase, ProjectCampaignActivationUseCase {
    private final StoreCampaignProjectionPort store;
    private final QueueCampaignRecoveryPort recoveryQueue;

    public CampaignProjectionService(StoreCampaignProjectionPort store,
            QueueCampaignRecoveryPort recoveryQueue) {
        this.store = Objects.requireNonNull(store, "store");
        this.recoveryQueue = Objects.requireNonNull(recoveryQueue, "recoveryQueue");
    }

    @Override
    public CampaignProjectionUpdateResult project(ApplyCampaignScheduledCommand command) {
        CampaignSaleProjection projection = CampaignSaleProjection.scheduled(
                command.campaignId(), command.aggregateVersion(), command.startsAt(), command.endsAt(),
                command.item(), command.occurredAt());
        return store.applyScheduled(projection);
    }

    @Override
    public CampaignProjectionUpdateResult project(ApplyCampaignActivatedCommand command) {
        CampaignProjectionUpdateResult result = store.applyActivated(
                command.campaignId(), command.aggregateVersion(), command.startsAt(), command.endsAt(),
                command.occurredAt());
        if (result == CampaignProjectionUpdateResult.RECOVERY_REQUIRED) {
            recoveryQueue.queue(command.campaignId(), command.occurredAt());
        }
        return result;
    }
}
