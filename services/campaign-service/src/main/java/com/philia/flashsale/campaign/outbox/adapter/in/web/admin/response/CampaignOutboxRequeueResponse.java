package com.philia.flashsale.campaign.outbox.adapter.in.web.admin.response;

import com.philia.flashsale.campaign.outbox.application.model.OutboxRequeueResult;
import java.util.UUID;

/** Public recovery metadata; the event payload remains private to the publisher. */
public record CampaignOutboxRequeueResponse(
        UUID eventId,
        UUID campaignId,
        String publishStatus,
        int retryCount,
        int requeueCount) {

    public static CampaignOutboxRequeueResponse from(OutboxRequeueResult result) {
        return new CampaignOutboxRequeueResponse(result.eventId(), result.campaignId(),
                result.publishStatus().name(), result.retryCount(), result.requeueCount());
    }
}
