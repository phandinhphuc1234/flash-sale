package com.philia.flashsale.campaign.scheduleoperation.application.exception;

import java.util.UUID;

/** Raised when another active scheduling operation already owns a Campaign. */
public final class ScheduleOperationInProgressException extends RuntimeException {

    private final UUID campaignId;

    public ScheduleOperationInProgressException(UUID campaignId) {
        super("A schedule operation is already in progress for Campaign " + campaignId);
        this.campaignId = campaignId;
    }

    public UUID campaignId() {
        return campaignId;
    }
}
