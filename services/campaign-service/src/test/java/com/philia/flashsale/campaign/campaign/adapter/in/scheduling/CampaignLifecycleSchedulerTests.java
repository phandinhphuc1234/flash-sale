package com.philia.flashsale.campaign.campaign.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.campaign.campaign.application.command.ActivateCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.command.EndCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.port.in.ActivateCampaignUseCase;
import com.philia.flashsale.campaign.campaign.application.port.in.EndCampaignUseCase;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignClockPort;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadDueCampaignsPort;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit coverage for the bounded, trace-aware lifecycle scan. */
@ExtendWith(MockitoExtension.class)
class CampaignLifecycleSchedulerTests {

    private static final Instant NOW = Instant.parse("2030-08-01T10:00:00Z");

    @Mock
    private LoadDueCampaignsPort dueCampaigns;

    @Mock
    private ActivateCampaignUseCase activateCampaign;

    @Mock
    private EndCampaignUseCase endCampaign;

    @Mock
    private CampaignClockPort clock;

    @Mock
    private Campaign campaign;

    @Test
    void scansAtConfiguredBatchSizeAndUsesSystemActorWithTrace() {
        UUID campaignId = UUID.randomUUID();
        when(clock.now()).thenReturn(NOW);
        when(campaign.id()).thenReturn(campaignId);
        when(campaign.version()).thenReturn(1L);
        when(dueCampaigns.findDueForActivation(NOW, 100)).thenReturn(List.of(campaign));
        when(dueCampaigns.findDueForEnding(NOW, 100)).thenReturn(List.of(campaign));

        new CampaignLifecycleScheduler(dueCampaigns, activateCampaign, endCampaign, clock, 100)
                .processDueCampaigns();

        ArgumentCaptor<ActivateCampaignCommand> activation = ArgumentCaptor.forClass(ActivateCampaignCommand.class);
        verify(activateCampaign).activate(activation.capture());
        assertThat(activation.getValue().campaignId()).isEqualTo(campaignId);
        assertThat(activation.getValue().expectedVersion()).isEqualTo(1L);
        assertThat(activation.getValue().actor()).isEqualTo("campaign-lifecycle-scheduler");
        assertThat(activation.getValue().traceId()).isNotBlank();
        assertThat(activation.getValue().manualRecovery()).isFalse();

        ArgumentCaptor<EndCampaignCommand> ending = ArgumentCaptor.forClass(EndCampaignCommand.class);
        verify(endCampaign).end(ending.capture());
        assertThat(ending.getValue().campaignId()).isEqualTo(campaignId);
        assertThat(ending.getValue().actor()).isEqualTo("campaign-lifecycle-scheduler");
        assertThat(ending.getValue().traceId()).isNotBlank();
    }
}
