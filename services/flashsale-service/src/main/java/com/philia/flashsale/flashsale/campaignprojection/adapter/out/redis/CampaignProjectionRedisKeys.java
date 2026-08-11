package com.philia.flashsale.flashsale.campaignprojection.adapter.out.redis;

import java.util.UUID;

/** Centralizes the single-Redis-instance key model for Campaign projection state. */
public final class CampaignProjectionRedisKeys {
    private CampaignProjectionRedisKeys() {
    }

    public static String meta(UUID campaignId) {
        return "fs:{hot}:campaign:" + campaignId + ":meta";
    }

    public static String stock(UUID campaignId) {
        return "fs:{hot}:campaign:" + campaignId + ":stock";
    }

    public static String item(UUID campaignId, UUID variantId) {
        return "fs:{hot}:campaign:" + campaignId + ":item:" + variantId;
    }

    public static String recoveryQueue() {
        return "fs:{hot}:projection-recovery";
    }
}
