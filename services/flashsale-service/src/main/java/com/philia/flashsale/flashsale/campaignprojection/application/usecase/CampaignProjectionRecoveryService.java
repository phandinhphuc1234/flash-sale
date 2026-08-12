package com.philia.flashsale.flashsale.campaignprojection.application.usecase;

import com.philia.flashsale.flashsale.campaignprojection.application.command.RecoverCampaignProjectionCommand;
import com.philia.flashsale.flashsale.campaignprojection.application.exception.CampaignSnapshotRecoveryException;
import com.philia.flashsale.flashsale.campaignprojection.application.port.in.RecoverCampaignProjectionUseCase;
import com.philia.flashsale.flashsale.campaignprojection.application.port.out.LoadCampaignSnapshotPort;
import com.philia.flashsale.flashsale.campaignprojection.application.port.out.QueueCampaignRecoveryPort;
import com.philia.flashsale.flashsale.campaignprojection.application.port.out.StoreCampaignProjectionPort;
import com.philia.flashsale.flashsale.campaignprojection.application.result.CampaignProjectionUpdateResult;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignSaleProjection;
import java.util.Optional;
import java.util.Objects;

/** Executes control-plane recovery; ordinary reservation requests never call this service. */
public final class CampaignProjectionRecoveryService implements RecoverCampaignProjectionUseCase {
    private final LoadCampaignSnapshotPort snapshotLoader;
    private final StoreCampaignProjectionPort store;
    private final QueueCampaignRecoveryPort recoveryQueue;

    public CampaignProjectionRecoveryService(LoadCampaignSnapshotPort snapshotLoader,
            StoreCampaignProjectionPort store, QueueCampaignRecoveryPort recoveryQueue) {
        this.snapshotLoader = Objects.requireNonNull(snapshotLoader, "snapshotLoader");
        this.store = Objects.requireNonNull(store, "store");
        this.recoveryQueue = Objects.requireNonNull(recoveryQueue, "recoveryQueue");
    }

    @Override
    public CampaignProjectionUpdateResult recover(RecoverCampaignProjectionCommand command) {
        final Optional<CampaignSaleProjection> snapshot;
        try {
            snapshot = snapshotLoader.load(command.campaignId());
        } catch (CampaignSnapshotRecoveryException exception) {
            recoveryQueue.queue(command.campaignId(), command.requestedAt().plus(exception.retryAfter()));
            return CampaignProjectionUpdateResult.RECOVERY_REQUIRED;
        }
        if (snapshot.isEmpty()) {
            recoveryQueue.queue(command.campaignId(), command.requestedAt());
            return CampaignProjectionUpdateResult.RECOVERY_REQUIRED;
        }
        CampaignProjectionUpdateResult result = store.applyRecovered(snapshot.get());
        if (result == CampaignProjectionUpdateResult.RECOVERY_REQUIRED) {
            recoveryQueue.queue(command.campaignId(), command.requestedAt());
        }
        return result;
    }
}
