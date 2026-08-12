package com.philia.flashsale.gateway.ratelimit;

import java.util.Objects;
import java.util.Optional;
import org.springframework.web.server.ServerWebExchange;

/**
 * Resolves one opaque bucket key for the configured policy.
 *
 * <p>The filter only receives a safe Redis key or an unavailable outcome. Raw
 * caller identity and HMAC details stay inside the resolver/key factory boundary.
 */
public interface RateLimitIdentityResolver {

    Optional<ResolvedRateLimitIdentity> resolve(ServerWebExchange exchange, RateLimitPolicy policy);

    record ResolvedRateLimitIdentity(String bucketKey) {

        public ResolvedRateLimitIdentity {
            if (bucketKey == null || bucketKey.isBlank()) {
                throw new IllegalArgumentException("bucket key must not be blank");
            }
            bucketKey = Objects.requireNonNull(bucketKey).trim();
        }
    }
}
