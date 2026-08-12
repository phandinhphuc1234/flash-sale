package com.philia.flashsale.campaign.campaign.application.exception;

import java.util.UUID;

/** Raised when an optimistic version supplied by an administrator is stale. */
public final class CampaignVersionConflictException extends RuntimeException {

    private final UUID campaignId;
    private final long expectedVersion;
    private final long actualVersion;

    public CampaignVersionConflictException(UUID campaignId, long expectedVersion, long actualVersion) {
        super("Campaign version is stale: expected " + expectedVersion + " but was " + actualVersion);
        this.campaignId = campaignId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public UUID campaignId() {
        return campaignId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long actualVersion() {
        return actualVersion;
    }
}
