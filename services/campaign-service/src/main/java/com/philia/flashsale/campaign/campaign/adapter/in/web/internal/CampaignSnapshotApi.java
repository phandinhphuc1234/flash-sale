package com.philia.flashsale.campaign.campaign.adapter.in.web.internal;

import com.philia.flashsale.campaign.campaign.adapter.in.web.internal.response.CampaignSnapshotResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Transport contract for the private Flash Sale recovery endpoint. */
@Tag(name = "Internal campaigns")
@SecurityRequirement(name = "bearerAuth")
public interface CampaignSnapshotApi {

    @Operation(summary = "Read a campaign snapshot", description = "Internal recovery endpoint; never expose through the public Gateway.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Campaign snapshot returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Campaign does not exist")
    })
    ResponseEntity<CampaignSnapshotResponse> getSnapshot(UUID campaignId);
}
