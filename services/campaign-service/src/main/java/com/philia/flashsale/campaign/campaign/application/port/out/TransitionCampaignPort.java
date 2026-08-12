package com.philia.flashsale.campaign.campaign.application.port.out;

import com.philia.flashsale.campaign.campaign.application.command.ActivateCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.command.EndCampaignCommand;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import java.util.Optional;

/** Atomic outbound boundary for lifecycle status/version changes and activation outbox creation. */
public interface TransitionCampaignPort {

    Optional<Campaign> activate(Campaign candidate, ActivateCampaignCommand command);

    Optional<Campaign> end(Campaign candidate, EndCampaignCommand command);
}
