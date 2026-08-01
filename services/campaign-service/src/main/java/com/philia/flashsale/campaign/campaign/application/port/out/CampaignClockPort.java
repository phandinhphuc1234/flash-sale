package com.philia.flashsale.campaign.campaign.application.port.out;

import java.time.Instant;

/** Outbound time source so use cases remain deterministic in tests. */
public interface CampaignClockPort {

    Instant now();
}
