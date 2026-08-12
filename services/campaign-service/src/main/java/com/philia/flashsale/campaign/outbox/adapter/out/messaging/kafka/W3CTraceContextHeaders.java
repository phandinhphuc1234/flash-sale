package com.philia.flashsale.campaign.outbox.adapter.out.messaging.kafka;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Builds a valid W3C traceparent while the service still stores its bounded correlation value. */
public final class W3CTraceContextHeaders {

    private W3CTraceContextHeaders() {
    }

    public static byte[] traceparent(String correlationId) {
        String traceId = normalize(correlationId, 32, "campaign-trace");
        String spanId = normalize(correlationId, 16, "campaign-outbox-span");
        return ("00-" + traceId + "-" + spanId + "-01").getBytes(StandardCharsets.UTF_8);
    }

    private static String normalize(String value, int length, String salt) {
        String candidate = value == null ? "" : value.replace("-", "").toLowerCase(Locale.ROOT);
        if (candidate.matches("[0-9a-f]+") && candidate.length() >= length) {
            String normalized = candidate.substring(0, length);
            if (!normalized.chars().allMatch(character -> character == '0')) {
                return normalized;
            }
        }
        String digest = digest(salt + ":" + value).substring(0, length);
        return digest.chars().allMatch(character -> character == '0')
                ? "1" + "0".repeat(length - 1)
                : digest;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for W3C trace propagation", exception);
        }
    }
}
