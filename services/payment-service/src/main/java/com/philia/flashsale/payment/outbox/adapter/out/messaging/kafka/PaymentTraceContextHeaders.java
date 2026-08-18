package com.philia.flashsale.payment.outbox.adapter.out.messaging.kafka;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** Keeps the Kafka boundary W3C-compatible even for provider webhooks without inbound trace data. */
final class PaymentTraceContextHeaders {

    private PaymentTraceContextHeaders() {
    }

    static byte[] traceparent(String stored, UUID eventId, UUID aggregateId) {
        if (stored != null && !stored.isBlank()) {
            if (!stored.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")) {
                throw new IllegalArgumentException("stored traceparent is malformed");
            }
            return stored.getBytes(StandardCharsets.UTF_8);
        }
        String digest = digest(eventId + ":" + aggregateId);
        String traceId = digest.substring(0, 32);
        String spanId = digest.substring(32, 48);
        return ("00-" + traceId + "-" + spanId + "-01").getBytes(StandardCharsets.UTF_8);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for W3C propagation", exception);
        }
    }
}
