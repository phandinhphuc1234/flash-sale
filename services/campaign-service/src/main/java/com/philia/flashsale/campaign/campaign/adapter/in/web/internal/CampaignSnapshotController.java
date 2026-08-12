package com.philia.flashsale.campaign.campaign.adapter.in.web.internal;

import com.philia.flashsale.campaign.campaign.adapter.in.web.internal.mapper.CampaignSnapshotWebMapper;
import com.philia.flashsale.campaign.campaign.adapter.in.web.internal.response.CampaignSnapshotResponse;
import com.philia.flashsale.campaign.campaign.application.port.in.GetCampaignSnapshotUseCase;
import com.philia.flashsale.campaign.campaign.application.query.GetCampaignSnapshotQuery;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Private HTTP adapter consumed only by Flash Sale service recovery. */
@RestController
@RequestMapping("/internal/v1/campaigns")
public class CampaignSnapshotController implements CampaignSnapshotApi {

    private final GetCampaignSnapshotUseCase getSnapshot;
    private final CampaignSnapshotWebMapper mapper;

    public CampaignSnapshotController(
            GetCampaignSnapshotUseCase getSnapshot,
            CampaignSnapshotWebMapper mapper) {
        this.getSnapshot = getSnapshot;
        this.mapper = mapper;
    }

    @Override
    @GetMapping("/{campaignId}/snapshot")
    public ResponseEntity<CampaignSnapshotResponse> getSnapshot(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(mapper.toResponse(
                getSnapshot.getSnapshot(new GetCampaignSnapshotQuery(campaignId))));
    }
}
