package com.philia.flashsale.campaign.scheduleoperation.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Stable SHA-256 identity of the canonical schedule command and Campaign configuration.
 *
 * <p>The caller owns canonicalization (field order and representation); this value object only
 * guarantees the persisted 64-character lowercase hexadecimal form.</p>
 */
public record ScheduleOperationFingerprint(String value) {

    public ScheduleOperationFingerprint {
        value = Objects.requireNonNull(value, "Schedule operation request hash is required")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Schedule operation request hash must be 64 lowercase hexadecimal characters");
        }
    }

    /** Hashes an already canonicalized command/configuration representation. */
    public static ScheduleOperationFingerprint fromCanonicalPayload(String canonicalPayload) {
        String normalized = Objects.requireNonNull(canonicalPayload, "Canonical schedule payload is required");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Canonical schedule payload must not be blank");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return new ScheduleOperationFingerprint(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the runtime", exception);
        }
    }
}
