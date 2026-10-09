package com.philia.flashsale.campaign.campaign.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.campaign.campaign.application.exception.CampaignNotFoundException;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadPublicCampaignsPort;
import com.philia.flashsale.campaign.campaign.application.query.BrowsePublicCampaignsQuery;
import com.philia.flashsale.campaign.campaign.application.query.GetPublicCampaignQuery;
import com.philia.flashsale.campaign.campaign.application.query.PublicCampaignFilter;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignPage;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignSnapshot;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignState;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicCampaignQueryServiceTests {

    private static final Instant NOW = Instant.parse("2026-10-09T10:30:00Z");
    private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void presentsAnActiveCompleteSnapshotAsReservableLiveOffer() {
        var snapshot = snapshot(CampaignStatus.ACTIVE, NOW.minusSeconds(60), NOW.plusSeconds(60));
        var service = new PublicCampaignQueryService(new StubPort(List.of(snapshot), Optional.of(snapshot)));

        var result = service.get(new GetPublicCampaignQuery(CAMPAIGN_ID, NOW));

        assertThat(result.phase()).isEqualTo(PublicCampaignState.LIVE);
        assertThat(result.reservable()).isTrue();
        assertThat(result.presentationAvailable()).isTrue();
    }

    @Test
    void schedulerLagDoesNotExposeAStoredScheduledCampaignAsReservable() {
        var snapshot = snapshot(CampaignStatus.SCHEDULED, NOW.minusSeconds(60), NOW.plusSeconds(60));
        var service = new PublicCampaignQueryService(new StubPort(List.of(snapshot), Optional.of(snapshot)));

        var result = service.browse(new BrowsePublicCampaignsQuery(PublicCampaignFilter.ALL, 0, 12, NOW));

        assertThat(result.data()).singleElement().satisfies(campaign -> {
            assertThat(campaign.phase()).isEqualTo(PublicCampaignState.LIVE);
            assertThat(campaign.reservable()).isFalse();
        });
    }

    @Test
    void draftDetailIsHiddenAsNotFound() {
        var draft = snapshot(CampaignStatus.DRAFT, NOW.plusSeconds(60), NOW.plusSeconds(120));
        var service = new PublicCampaignQueryService(new StubPort(List.of(), Optional.of(draft)));
        var query = new GetPublicCampaignQuery(CAMPAIGN_ID, NOW);

        assertThatThrownBy(() -> service.get(query))
                .isInstanceOf(CampaignNotFoundException.class);
    }

    private static PublicCampaignSnapshot snapshot(CampaignStatus status, Instant startAt, Instant endAt) {
        return new PublicCampaignSnapshot(
                CAMPAIGN_ID,
                "Phone weekend",
                status,
                startAt,
                endAt,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PHONE-BLACK",
                new BigDecimal("229000"),
                new BigDecimal("179000"),
                "VND",
                1);
    }

    private record StubPort(
            List<PublicCampaignSnapshot> visible,
            Optional<PublicCampaignSnapshot> detail) implements LoadPublicCampaignsPort {
        @Override
        public PublicCampaignPage<PublicCampaignSnapshot> loadVisible(
                PublicCampaignFilter filter, Instant now, int page, int size) {
            return new PublicCampaignPage<>(visible, page, size, visible.size());
        }

        @Override
        public Optional<PublicCampaignSnapshot> loadPublicDetail(UUID campaignId) {
            return detail;
        }
    }
}
