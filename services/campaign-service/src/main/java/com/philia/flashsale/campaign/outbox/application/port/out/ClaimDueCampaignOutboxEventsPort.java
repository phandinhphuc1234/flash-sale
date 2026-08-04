package com.philia.flashsale.campaign.outbox.application.port.out;

import com.philia.flashsale.campaign.outbox.application.model.OutboxClaim;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Claims due rows with an expiring lease so multiple publisher instances do not duplicate work. */
public interface ClaimDueCampaignOutboxEventsPort {

    List<OutboxClaim> claimDue(String workerId, Instant now, Duration lease, int batchSize);
}
