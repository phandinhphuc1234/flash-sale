package com.philia.flashsale.campaign.outbox.application.port.out;

import com.philia.flashsale.campaign.outbox.application.model.CampaignOutboxEvent;

/** Outbound boundary for saving an already-serialized Campaign outbox record. */
public interface SaveCampaignOutboxEventPort {

    void save(CampaignOutboxEvent event);
}
