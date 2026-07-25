package com.philia.flashsale.gateway.ratelimit;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.springframework.web.server.ServerWebExchange;

/**
 * Resolves the direct socket peer into one opaque rate-limit bucket key.
 *
 * <p>This resolver deliberately ignores {@code Forwarded} and
 * {@code X-Forwarded-For}. Trusted proxy support is a separate future feature.
 */
public final class DirectClientIpRateLimitIdentityResolver implements RateLimitIdentityResolver {

    private static final byte[] IPV4_MAPPED_PREFIX =
            new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff};

    private final HmacRateLimitBucketKeyFactory bucketKeyFactory;

    public DirectClientIpRateLimitIdentityResolver(HmacRateLimitBucketKeyFactory bucketKeyFactory) {
        this.bucketKeyFactory = Objects.requireNonNull(bucketKeyFactory, "bucket key factory must not be null");
    }

    @Override
    public Optional<ResolvedRateLimitIdentity> resolve(ServerWebExchange exchange, RateLimitPolicy policy) {
        Objects.requireNonNull(exchange, "server exchange must not be null");
        Objects.requireNonNull(policy, "rate-limit policy must not be null");

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return Optional.empty();
        }

        byte[] addressBytes = normalizedAddressBytes(remoteAddress.getAddress());
        if (addressBytes.length != 4 && addressBytes.length != 16) {
            return Optional.empty();
        }

        return Optional.of(new ResolvedRateLimitIdentity(bucketKeyFactory.bucketKey(policy, addressBytes)));
    }

    private static byte[] normalizedAddressBytes(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 16 && isIpv4Mapped(bytes)) {
            return Arrays.copyOfRange(bytes, 12, 16);
        }
        return bytes;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int index = 0; index < IPV4_MAPPED_PREFIX.length; index++) {
            if (bytes[index] != IPV4_MAPPED_PREFIX[index]) {
                return false;
            }
        }
        return true;
    }
}
