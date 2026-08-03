package com.philia.flashsale.campaign.campaign.application.usecase;

import com.philia.flashsale.campaign.campaign.application.exception.CampaignSnapshotNotFoundException;
import com.philia.flashsale.campaign.campaign.application.port.in.GetCampaignSnapshotUseCase;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadCampaignSnapshotPort;
import com.philia.flashsale.campaign.campaign.application.query.GetCampaignSnapshotQuery;
import com.philia.flashsale.campaign.campaign.application.result.CampaignSnapshotResult;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** Reads the immutable, complete Campaign projection used by Flash Sale recovery. */
public class GetCampaignSnapshotService implements GetCampaignSnapshotUseCase {

    private final LoadCampaignSnapshotPort snapshotPort;

    public GetCampaignSnapshotService(LoadCampaignSnapshotPort snapshotPort) {
        this.snapshotPort = Objects.requireNonNull(snapshotPort);
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignSnapshotResult getSnapshot(GetCampaignSnapshotQuery query) {
        Objects.requireNonNull(query, "Campaign snapshot query is required");
        if (query.campaignId() == null) {
            throw new IllegalArgumentException("Campaign id is required");
        }
        return snapshotPort.findCompleteSnapshotById(query.campaignId())
                .map(CampaignSnapshotResult::from)
                .orElseThrow(() -> new CampaignSnapshotNotFoundException(query.campaignId()));
    }
}
