package com.philia.flashsale.campaign.outbox.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Records a sanitized publication failure and applies the approved retry policy. */
public interface RecordCampaignOutboxFailurePort {

    boolean recordFailure(UUID eventId, String workerId, Instant now, String failureMessage,
            int maxAutomaticAttempts, Duration retryBackoffCap);
}
