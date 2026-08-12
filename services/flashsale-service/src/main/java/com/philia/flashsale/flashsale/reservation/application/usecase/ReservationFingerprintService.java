package com.philia.flashsale.flashsale.reservation.application.usecase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Creates the two non-secret SHA-256 values used by the Redis and durable idempotency boundaries. */
public final class ReservationFingerprintService {
    private static final HexFormat HEX = HexFormat.of();

    public String hashIdempotencyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank() || rawKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must contain 1..128 characters");
        }
        return sha256(rawKey);
    }

    /** The canonical request intentionally excludes timestamps, trace, authorization, and generated IDs. */
    public String hashRequest(UUID userId, UUID campaignId, UUID variantId, long quantity) {
        if (userId == null || campaignId == null || variantId == null || quantity <= 0) {
            throw new IllegalArgumentException("canonical reservation fields are invalid");
        }
        String canonical = userId + "|" + campaignId + "|" + variantId + "|" + quantity;
        return sha256(canonical);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
