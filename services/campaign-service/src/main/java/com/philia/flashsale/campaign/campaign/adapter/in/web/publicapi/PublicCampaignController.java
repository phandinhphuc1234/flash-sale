package com.philia.flashsale.campaign.campaign.adapter.in.web.publicapi;

import com.philia.flashsale.campaign.campaign.application.port.in.BrowsePublicCampaignsUseCase;
import com.philia.flashsale.campaign.campaign.application.port.in.GetPublicCampaignUseCase;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignClockPort;
import com.philia.flashsale.campaign.campaign.application.query.BrowsePublicCampaignsQuery;
import com.philia.flashsale.campaign.campaign.application.query.GetPublicCampaignQuery;
import com.philia.flashsale.campaign.campaign.application.query.PublicCampaignFilter;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignPage;
import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Anonymous shopper read boundary for live and upcoming Campaign discovery. */
@RestController
@RequestMapping("/api/v1/campaigns")
@Tag(name = "Public campaigns", description = "Shopper-safe Flash Sale discovery")
public class PublicCampaignController {

    private final BrowsePublicCampaignsUseCase browseCampaigns;
    private final GetPublicCampaignUseCase getCampaign;
    private final CampaignClockPort clock;

    public PublicCampaignController(
            BrowsePublicCampaignsUseCase browseCampaigns,
            GetPublicCampaignUseCase getCampaign,
            CampaignClockPort clock) {
        this.browseCampaigns = browseCampaigns;
        this.getCampaign = getCampaign;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "Browse public Flash Sale campaigns",
            description = "Returns a bounded, deterministic page of live and upcoming Campaign offers.")
    public ApiResponse<PageResponse<PublicCampaignResponse>> browse(
            @RequestParam(defaultValue = "ALL") PublicCampaignFilter phase,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        PublicCampaignPage<PublicCampaignResponse> result = browseCampaigns.browse(
                        new BrowsePublicCampaignsQuery(phase, page, size, clock.now()))
                .map(PublicCampaignResponse::from);
        return ApiResponse.success(PageResponse.of(
                result.data(), result.number(), result.size(), result.totalElements()));
    }

    @GetMapping("/{campaignId}")
    @Operation(summary = "View public Flash Sale campaign",
            description = "Returns a shopper-safe offer view without exposing exact stock or admin metadata.")
    public ApiResponse<PublicCampaignResponse> detail(@PathVariable UUID campaignId) {
        return ApiResponse.success(PublicCampaignResponse.from(
                getCampaign.get(new GetPublicCampaignQuery(campaignId, clock.now()))));
    }
}
