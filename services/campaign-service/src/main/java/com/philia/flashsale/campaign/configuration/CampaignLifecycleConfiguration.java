package com.philia.flashsale.campaign.configuration;

import com.philia.flashsale.campaign.campaign.application.port.out.LoadCampaignPort;
import com.philia.flashsale.campaign.campaign.application.port.out.TransitionCampaignPort;
import com.philia.flashsale.campaign.campaign.application.usecase.CampaignLifecycleService;
import com.philia.flashsale.campaign.campaign.domain.policy.CampaignLifecyclePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition-root wiring for activation and ending use cases. */
@Configuration
public class CampaignLifecycleConfiguration {

    @Bean
    CampaignLifecyclePolicy campaignLifecyclePolicy() {
        return new CampaignLifecyclePolicy();
    }

    @Bean
    CampaignLifecycleService campaignLifecycleService(
            LoadCampaignPort campaignPort,
            TransitionCampaignPort transitionPort,
            CampaignLifecyclePolicy lifecyclePolicy) {
        return new CampaignLifecycleService(campaignPort, transitionPort, lifecyclePolicy);
    }
}
