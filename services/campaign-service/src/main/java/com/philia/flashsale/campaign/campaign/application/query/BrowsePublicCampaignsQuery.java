package com.philia.flashsale.campaign.campaign.application.query;

import java.time.Instant;
import java.util.Objects;

/** Bounded public discovery query. */
public record BrowsePublicCampaignsQuery(
        PublicCampaignFilter filter,
        int page,
        int size,
        Instant now) {

    public static final int DEFAULT_SIZE = 12;
    public static final int MAX_SIZE = 50;

    public BrowsePublicCampaignsQuery {
        filter = filter == null ? PublicCampaignFilter.ALL : filter;
        Objects.requireNonNull(now, "Current time is required");
        if (page < 0) {
            throw new IllegalArgumentException("Page must not be negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_SIZE);
        }
    }
}
