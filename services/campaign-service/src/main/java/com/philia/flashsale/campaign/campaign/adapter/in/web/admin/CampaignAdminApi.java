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

/**
 * Transport-level contract for the Campaign administrator draft API.
 *
 * <p>The implementation owns Spring MVC annotations while this interface keeps the public
 * endpoint signatures discoverable and separate from controller orchestration.</p>
 */
public interface CampaignAdminApi {

    ResponseEntity<ApiResponse<CampaignResponse>> create(CreateCampaignRequest request);

    ResponseEntity<ApiResponse<CampaignResponse>> replaceMetadata(
            UUID campaignId, String ifMatch, ReplaceCampaignMetadataRequest request);

    ResponseEntity<ApiResponse<CampaignResponse>> replaceItem(
            UUID campaignId, String ifMatch, ReplaceCampaignItemRequest request);

    ResponseEntity<ApiResponse<CampaignResponse>> getDetail(UUID campaignId);

    ResponseEntity<ApiResponse<CampaignResponse>> schedule(
            UUID campaignId, String ifMatch, String idempotencyKey, ScheduleCampaignRequest request,
            String traceId);

    ResponseEntity<ApiResponse<CampaignResponse>> activate(
            UUID campaignId, String ifMatch, ActivateCampaignRequest request, String traceId);
}
