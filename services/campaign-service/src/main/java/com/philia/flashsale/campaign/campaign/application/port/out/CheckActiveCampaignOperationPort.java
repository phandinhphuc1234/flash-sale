package com.philia.flashsale.campaign.campaign.application.port.out;

import java.util.UUID;

/** Outbound boundary for blocking draft edits while a schedule operation is active. */
public interface CheckActiveCampaignOperationPort {

    boolean hasActiveOperation(UUID campaignId);
}
