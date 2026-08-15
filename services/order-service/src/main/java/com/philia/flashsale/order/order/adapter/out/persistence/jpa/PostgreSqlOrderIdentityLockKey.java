package com.philia.flashsale.order.order.adapter.out.persistence.jpa;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

/**
 * Derives stable PostgreSQL advisory-lock keys for Order identity arbitration.
 *
 * <p>The namespace is part of the digest input so a purchase-request identity and a
 * reservation identity with the same UUID cannot accidentally share a lock domain.
 * PostgreSQL accepts the signed {@code BIGINT} result directly for {@code pg_advisory_xact_lock}.
 */
public final class PostgreSqlOrderIdentityLockKey {

    public static final String PURCHASE_REQUEST_NAMESPACE = "order:purchase-request";
    public static final String RESERVATION_NAMESPACE = "order:reservation";

    private PostgreSqlOrderIdentityLockKey() {
    }

    public static long forPurchaseRequest(UUID purchaseRequestId) {
        return from(PURCHASE_REQUEST_NAMESPACE, purchaseRequestId);
    }

    public static long forReservation(UUID reservationId) {
        return from(RESERVATION_NAMESPACE, reservationId);
    }

    static long from(String namespace, UUID identity) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(identity, "identity");
        if (namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }

        byte[] namespaceBytes = namespace.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocate(Integer.BYTES + namespaceBytes.length + Long.BYTES * 2);
        input.putInt(namespaceBytes.length).put(namespaceBytes);
        input.putLong(identity.getMostSignificantBits()).putLong(identity.getLeastSignificantBits());

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.array());
            return ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }
}
