package com.philia.flashsale.campaign.campaign.application.port.out;

/** Outbound identity source for the administrator/system actor performing a mutation. */
public interface CampaignActorPort {

    String currentActor();
}
