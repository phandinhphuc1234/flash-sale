package com.philia.flashsale.campaign.campaign.application.port.out;

import com.philia.flashsale.campaign.campaign.application.command.FinalizeCampaignSchedulingCommand;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;

/** Outbound boundary for the local transaction that commits scheduling and its outbox row. */
public interface FinalizeCampaignSchedulingPort {

    Campaign finalizeSchedule(FinalizeCampaignSchedulingCommand command);
}
