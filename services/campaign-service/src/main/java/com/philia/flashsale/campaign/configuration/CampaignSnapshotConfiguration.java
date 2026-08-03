package com.philia.flashsale.campaign.configuration;

import com.philia.flashsale.campaign.campaign.application.port.out.LoadCampaignSnapshotPort;
import com.philia.flashsale.campaign.campaign.application.usecase.GetCampaignSnapshotService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition-root wiring for the internal Campaign snapshot query. */
@Configuration
public class CampaignSnapshotConfiguration {

    @Bean
    GetCampaignSnapshotService getCampaignSnapshotService(LoadCampaignSnapshotPort snapshotPort) {
        return new GetCampaignSnapshotService(snapshotPort);
    }
}
