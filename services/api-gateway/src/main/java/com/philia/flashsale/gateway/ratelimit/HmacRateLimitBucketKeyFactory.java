package com.philia.flashsale.gateway.ratelimit;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Creates opaque Redis bucket keys from sensitive identity bytes.
 *
 * <p>The raw identity is length-prefixed inside the HMAC input and never appears
 * in the returned key. A fresh {@link Mac} instance is initialized per call
 * because {@code Mac} is mutable and must not be shared across WebFlux requests.
 */
public final class HmacRateLimitBucketKeyFactory {

    private static final String KEY_SCHEMA_MARKER = "gateway-rate-limit-key-k1";
    private static final String KEY_SCHEMA_VERSION = "k1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    private final String keyPrefix;
    private final String environment;
    private final byte[] secret;

    public HmacRateLimitBucketKeyFactory(String keyPrefix, String environment, byte[] secret) {
        this.keyPrefix = requireText(keyPrefix, "key prefix");
        this.environment = requireText(environment, "environment");
        this.secret = Objects.requireNonNull(secret, "hmac secret must not be null").clone();
        if (this.secret.length < 32) {
            throw new IllegalArgumentException("hmac secret must contain at least 32 bytes");
        }
    }

    public String bucketKey(RateLimitPolicy policy, byte[] addressBytes) {
        Objects.requireNonNull(policy, "rate-limit policy must not be null");
        Objects.requireNonNull(addressBytes, "address bytes must not be null");
        if (addressBytes.length != 4 && addressBytes.length != 16) {
            throw new IllegalArgumentException("address bytes must be IPv4 or IPv6 length");
        }

        byte[] input = hmacInput(policy, addressBytes);
        byte[] digest = initializedMac().doFinal(input);
        return keyPrefix + ":" + KEY_SCHEMA_VERSION + ":" + HEX.formatHex(digest);
    }

    private byte[] hmacInput(RateLimitPolicy policy, byte[] addressBytes) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeSegment(output, KEY_SCHEMA_MARKER.getBytes(StandardCharsets.UTF_8));
        writeSegment(output, environment.getBytes(StandardCharsets.UTF_8));
        writeSegment(output, policy.stateVersion().getBytes(StandardCharsets.UTF_8));
        writeSegment(output, policy.id().getBytes(StandardCharsets.UTF_8));
        writeSegment(output, policy.identityStrategy().getBytes(StandardCharsets.UTF_8));
        writeSegment(output, addressBytes);
        return output.toByteArray();
    }

    private Mac initializedMac() {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("rate-limit HMAC initialization failed", exception);
        }
    }

    private static void writeSegment(ByteArrayOutputStream output, byte[] value) {
        output.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        output.writeBytes(value);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    @Override
    public String toString() {
        return "HmacRateLimitBucketKeyFactory[keyPrefix=%s, environment=%s, secret=%s]"
                .formatted(keyPrefix, environment, "[redacted]");
    }
}
