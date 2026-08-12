package com.philia.flashsale.campaign.campaign.application.port.out;

import com.philia.flashsale.campaign.campaign.domain.model.Campaign;

/** Outbound boundary for persisting a Campaign aggregate. */
public interface SaveCampaignPort {

    Campaign save(Campaign campaign);
}
