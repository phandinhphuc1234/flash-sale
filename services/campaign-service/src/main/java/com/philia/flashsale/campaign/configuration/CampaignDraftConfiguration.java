package com.philia.flashsale.campaign.configuration;

import com.philia.flashsale.campaign.campaign.application.port.out.CampaignActorPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignClockPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CheckActiveCampaignOperationPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CheckCampaignCodeUniquenessPort;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadCampaignPort;
import com.philia.flashsale.campaign.campaign.application.port.out.SaveCampaignPort;
import com.philia.flashsale.campaign.campaign.application.usecase.CampaignDraftApplicationService;
import com.philia.flashsale.campaign.scheduleoperation.adapter.out.persistence.jpa.repository.CampaignScheduleOperationJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Wires the Campaign draft use case without leaking Spring concerns into its core. */
@Configuration
public class CampaignDraftConfiguration {

    @Bean
    CampaignDraftApplicationService campaignDraftApplicationService(
            LoadCampaignPort loadCampaignPort,
            SaveCampaignPort saveCampaignPort,
            CheckCampaignCodeUniquenessPort codeUniquenessPort,
            CheckActiveCampaignOperationPort activeOperationPort,
            CampaignClockPort clockPort,
            CampaignActorPort actorPort) {
        return new CampaignDraftApplicationService(
                loadCampaignPort,
                saveCampaignPort,
                codeUniquenessPort,
                activeOperationPort,
                clockPort,
                actorPort);
    }

    @Bean
    CampaignClockPort campaignClockPort() {
        return Instant::now;
    }

    @Bean
    CampaignActorPort campaignActorPort() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null
                    || authentication.getName().isBlank()) {
                return "system";
            }
            return authentication.getName();
        };
    }

    /** Bridges the campaign use case to schedule-operation state at the composition root. */
    @Bean
    CheckActiveCampaignOperationPort checkActiveCampaignOperationPort(
            CampaignScheduleOperationJpaRepository repository) {
        return (UUID campaignId) -> repository.existsByCampaignIdAndOperationStatusIn(
                campaignId, List.of("STARTED", "INVENTORY_ALLOCATED"));
    }
}
