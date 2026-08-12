package com.philia.flashsale.campaign.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.philia.flashsale.campaign.campaign.application.exception.CampaignSnapshotNotFoundException;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadCampaignSnapshotPort;
import com.philia.flashsale.campaign.campaign.application.query.GetCampaignSnapshotQuery;
import com.philia.flashsale.campaign.campaign.application.result.CampaignSnapshotResult;
import com.philia.flashsale.campaign.campaign.application.usecase.GetCampaignSnapshotService;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignItem;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignMoney;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the non-draft complete-snapshot application boundary. */
class GetCampaignSnapshotServiceTests {

    private final LoadCampaignSnapshotPort snapshots = mock(LoadCampaignSnapshotPort.class);
    private final GetCampaignSnapshotService service = new GetCampaignSnapshotService(snapshots);

    @Test
    void returnsOnlyTheStableRecoveryFields() {
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = completeCampaign(campaignId, CampaignStatus.SCHEDULED);
        when(snapshots.findCompleteSnapshotById(campaignId)).thenReturn(Optional.of(campaign));

        CampaignSnapshotResult result = service.getSnapshot(new GetCampaignSnapshotQuery(campaignId));

        assertThat(result.campaignId()).isEqualTo(campaignId);
        assertThat(result.status()).isEqualTo(CampaignStatus.SCHEDULED);
        assertThat(result.item().variantSku()).isEqualTo("SKU-1");
        assertThat(result.item().campaignPrice()).isEqualByComparingTo("900.0000");
        assertThat(result.item().currency()).isEqualTo("VND");
        assertThat(result.item().allocatedQuantity()).isEqualTo(10);
    }

    @Test
    void mapsMissingOrIncompleteSnapshotsToTheApprovedNotFoundFailure() {
        UUID campaignId = UUID.randomUUID();
        when(snapshots.findCompleteSnapshotById(campaignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSnapshot(new GetCampaignSnapshotQuery(campaignId)))
                .isInstanceOf(CampaignSnapshotNotFoundException.class);
    }

    private static Campaign completeCampaign(UUID id, CampaignStatus status) {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        CampaignItem item = CampaignItem.rehydrate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SKU-1",
                CampaignMoney.vnd(new BigDecimal("1000.0000")),
                CampaignMoney.vnd(new BigDecimal("900.0000")),
                10,
                10,
                1);
        return Campaign.rehydrate(
                id,
                "CAMPAIGN-1",
                "Campaign",
                now.plusSeconds(60),
                now.plusSeconds(3600),
                status,
                now,
                status == CampaignStatus.ACTIVE ? now : null,
                status == CampaignStatus.ENDED ? now.plusSeconds(3600) : null,
                3,
                "admin",
                "admin",
                now,
                now,
                item);
    }
}
