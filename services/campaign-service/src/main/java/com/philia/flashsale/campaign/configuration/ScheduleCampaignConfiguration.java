package com.philia.flashsale.campaign.configuration;

import com.philia.flashsale.campaign.campaign.application.port.out.AllocateCampaignInventoryPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignActorPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignClockPort;
import com.philia.flashsale.campaign.campaign.application.port.out.FinalizeCampaignSchedulingPort;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadCampaignPort;
import com.philia.flashsale.campaign.campaign.application.port.out.ValidateCampaignVariantPort;
import com.philia.flashsale.campaign.campaign.application.usecase.ScheduleCampaignService;
import com.philia.flashsale.campaign.scheduleoperation.application.port.in.PrepareScheduleOperationUseCase;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.LoadScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.SaveScheduleOperationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Composition-root wiring for the schedule orchestration use case. */
@Configuration
@EnableScheduling
public class ScheduleCampaignConfiguration {

    @Bean
    ScheduleCampaignService scheduleCampaignService(
            LoadCampaignPort campaignPort,
            LoadScheduleOperationPort operationPort,
            SaveScheduleOperationPort operationSavePort,
            PrepareScheduleOperationUseCase operationPreparation,
            ValidateCampaignVariantPort productPort,
            AllocateCampaignInventoryPort inventoryPort,
            FinalizeCampaignSchedulingPort finalizationPort,
            CampaignActorPort actorPort,
            CampaignClockPort clockPort) {
        return new ScheduleCampaignService(
                campaignPort, operationPort, operationSavePort, operationPreparation,
                productPort, inventoryPort, finalizationPort, actorPort, clockPort);
    }
}
