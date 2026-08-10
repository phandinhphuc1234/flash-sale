package com.philia.flashsale.flashsale.observability;

/** Low-cardinality observation names reserved for the Flash Sale runtime boundaries. */
public final class FlashSaleObservationNames {
    public static final String RESERVATION_ADMISSION = "flashsale.reservation.admission";
    public static final String CAMPAIGN_PROJECTION = "flashsale.campaign.projection";
    public static final String CAMPAIGN_RECOVERY = "flashsale.campaign.recovery";
    public static final String REDIS_LUA = "flashsale.redis.lua";
    public static final String REDIS_HANDOFF = "flashsale.redis.handoff";
    public static final String POSTGRES_ACCEPTANCE = "flashsale.postgres.acceptance";
    public static final String OUTBOX_PUBLICATION = "flashsale.outbox.publication";

    private FlashSaleObservationNames() {
    }
}
