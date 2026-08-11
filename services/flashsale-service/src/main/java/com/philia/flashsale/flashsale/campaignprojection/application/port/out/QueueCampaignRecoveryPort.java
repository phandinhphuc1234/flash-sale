package com.philia.flashsale.flashsale.campaignprojection.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Records that a Campaign requires an out-of-band snapshot recovery attempt. */
public interface QueueCampaignRecoveryPort {
    void queue(UUID campaignId, Instant nextAttemptAt);
}
