package com.philia.flashsale.campaign.configuration;

import com.philia.flashsale.campaign.campaign.application.port.out.LoadPublicCampaignsPort;
import com.philia.flashsale.campaign.campaign.application.usecase.PublicCampaignQueryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root for shopper-facing Campaign reads. */
@Configuration
public class PublicCampaignConfiguration {

    @Bean
    PublicCampaignQueryService publicCampaignQueryService(LoadPublicCampaignsPort campaigns) {
        return new PublicCampaignQueryService(campaigns);
    }
}
