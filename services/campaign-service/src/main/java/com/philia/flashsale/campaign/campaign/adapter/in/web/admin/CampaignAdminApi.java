package com.philia.flashsale.campaign.campaign.adapter.in.web.admin;

import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.request.CreateCampaignRequest;
import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.request.ActivateCampaignRequest;
import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.request.ReplaceCampaignItemRequest;
import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.request.ReplaceCampaignMetadataRequest;
import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.request.ScheduleCampaignRequest;
import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.response.CampaignResponse;
import com.philia.flashsale.common.web.ApiResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Transport-level contract for the Campaign administrator draft API.
 *
 * <p>The implementation owns Spring MVC annotations while this interface keeps the public
 * endpoint signatures discoverable and separate from controller orchestration.</p>
 */
@Tag(name = "Admin campaigns")
@SecurityRequirement(name = "bearerAuth")
public interface CampaignAdminApi {

    @Operation(summary = "Create a campaign draft")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Campaign created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid campaign draft")
    })
    ResponseEntity<ApiResponse<CampaignResponse>> create(CreateCampaignRequest request);

    @Operation(summary = "Update campaign metadata", description = "Uses the quoted If-Match campaign version for optimistic concurrency.")
    ResponseEntity<ApiResponse<CampaignResponse>> replaceMetadata(
            UUID campaignId, String ifMatch, ReplaceCampaignMetadataRequest request);

    @Operation(summary = "Replace campaign item", description = "Uses the quoted If-Match campaign version for optimistic concurrency.")
    ResponseEntity<ApiResponse<CampaignResponse>> replaceItem(
            UUID campaignId, String ifMatch, ReplaceCampaignItemRequest request);

    @Operation(summary = "Read campaign detail")
    ResponseEntity<ApiResponse<CampaignResponse>> getDetail(UUID campaignId);

    @Operation(summary = "Schedule a campaign", description = "Schedules a draft using an idempotency key and quoted version.")
    ResponseEntity<ApiResponse<CampaignResponse>> schedule(
            UUID campaignId, String ifMatch, String idempotencyKey, ScheduleCampaignRequest request,
            String traceId);

    @Operation(summary = "Activate a campaign", description = "Activates a scheduled campaign using the current quoted version.")
    ResponseEntity<ApiResponse<CampaignResponse>> activate(
            UUID campaignId, String ifMatch, ActivateCampaignRequest request, String traceId);
}
