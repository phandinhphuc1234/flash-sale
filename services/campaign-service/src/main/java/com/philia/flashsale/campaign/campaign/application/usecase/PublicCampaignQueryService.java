package com.philia.flashsale.campaign.campaign.application.usecase;

import com.philia.flashsale.campaign.campaign.application.exception.CampaignNotFoundException;
import com.philia.flashsale.campaign.campaign.application.port.in.BrowsePublicCampaignsUseCase;
import com.philia.flashsale.campaign.campaign.application.port.in.GetPublicCampaignUseCase;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadPublicCampaignsPort;
import com.philia.flashsale.campaign.campaign.application.query.BrowsePublicCampaignsQuery;
import com.philia.flashsale.campaign.campaign.application.query.GetPublicCampaignQuery;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignPage;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignResult;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignSnapshot;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignState;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus;
import java.time.Instant;
import java.util.Objects;

/** Derives shopper-safe lifecycle state from Campaign-owned durable snapshots. */
public final class PublicCampaignQueryService
        implements BrowsePublicCampaignsUseCase, GetPublicCampaignUseCase {

    private final LoadPublicCampaignsPort campaigns;

    public PublicCampaignQueryService(LoadPublicCampaignsPort campaigns) {
        this.campaigns = Objects.requireNonNull(campaigns);
    }

    @Override
    public PublicCampaignPage<PublicCampaignResult> browse(BrowsePublicCampaignsQuery query) {
        Objects.requireNonNull(query, "Browse query is required");
        return campaigns.loadVisible(query.filter(), query.now(), query.page(), query.size())
                .map(snapshot -> present(snapshot, query.now()));
    }

    @Override
    public PublicCampaignResult get(GetPublicCampaignQuery query) {
        Objects.requireNonNull(query, "Detail query is required");
        PublicCampaignSnapshot snapshot = campaigns.loadPublicDetail(query.campaignId())
                .filter(value -> value.storedStatus() != CampaignStatus.DRAFT)
                .orElseThrow(() -> new CampaignNotFoundException(query.campaignId()));
        return present(snapshot, query.now());
    }

    private PublicCampaignResult present(PublicCampaignSnapshot snapshot, Instant now) {
        boolean presentationAvailable = snapshot.productId() != null
                && snapshot.variantId() != null
                && snapshot.variantSku() != null
                && !snapshot.variantSku().isBlank()
                && snapshot.basePrice() != null
                && snapshot.campaignPrice() != null
                && snapshot.currency() != null
                && !snapshot.currency().isBlank();
        PublicCampaignState state = state(snapshot, now);
        boolean reservable = snapshot.storedStatus() == CampaignStatus.ACTIVE
                && state == PublicCampaignState.LIVE
                && presentationAvailable;
        return new PublicCampaignResult(
                snapshot.id(),
                snapshot.name(),
                state,
                snapshot.startAt(),
                snapshot.endAt(),
                reservable,
                snapshot.productId(),
                snapshot.variantId(),
                snapshot.variantSku(),
                snapshot.basePrice(),
                snapshot.campaignPrice(),
                snapshot.currency(),
                snapshot.purchaseLimitPerUser(),
                presentationAvailable);
    }

    private PublicCampaignState state(PublicCampaignSnapshot snapshot, Instant now) {
        if (snapshot.storedStatus() == CampaignStatus.ENDED || !now.isBefore(snapshot.endAt())) {
            return PublicCampaignState.ENDED;
        }
        if (!now.isBefore(snapshot.startAt())) {
            return PublicCampaignState.LIVE;
        }
        return PublicCampaignState.UPCOMING;
    }
}
