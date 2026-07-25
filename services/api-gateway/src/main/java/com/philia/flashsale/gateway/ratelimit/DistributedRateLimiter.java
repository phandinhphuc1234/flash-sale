package com.philia.flashsale.gateway.ratelimit;

import reactor.core.publisher.Mono;

/**
 * Redis-independent quota acquisition boundary.
 *
 * <p>The Gateway filter depends on this seam, while the Redis implementation
 * stays behind it in later tasks.
 */
public interface DistributedRateLimiter {

    Mono<RateLimitDecision> acquire(RateLimitPolicy policy, String bucketKey);
}
