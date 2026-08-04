package com.philia.flashsale.campaign.outbox.application.model;

import java.util.UUID;

/** Stable response data for an operator requeue without exposing the event payload. */
public record OutboxRequeueResult(
        UUID eventId,
        UUID campaignId,
        OutboxPublishStatus publishStatus,
        int retryCount,
        int requeueCount) {
}
