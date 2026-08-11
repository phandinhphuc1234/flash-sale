package com.philia.flashsale.flashsale.reservation.adapter.out.redis;

import java.util.UUID;

/** Builds the single-hash-tag Redis keys used by one atomic reservation script. */
public final class ReservationRedisKeys {
    private ReservationRedisKeys() {
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

    public static String userQuantity(UUID campaignId, UUID userId) {
        return "fs:{hot}:campaign:" + campaignId + ":user:" + userId + ":qty";
    }

    public static String idempotency(UUID campaignId, UUID userId, String idempotencyKeyHash) {
        return "fs:{hot}:campaign:" + campaignId + ":idem:" + userId + ":" + idempotencyKeyHash;
    }

    public static String reservation(UUID campaignId, UUID reservationId) {
        return "fs:{hot}:campaign:" + campaignId + ":reservation:" + reservationId;
    }

    public static String expirations() {
        return "fs:{hot}:expirations";
    }

    public static String handoff() {
        return "fs:{hot}:handoff";
    }
}
