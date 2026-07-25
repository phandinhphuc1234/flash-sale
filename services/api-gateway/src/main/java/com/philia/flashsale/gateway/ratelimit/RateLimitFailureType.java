package com.philia.flashsale.gateway.ratelimit;

/**
 * Finite set of coordinator failures that may use the approved fail-open path.
 */
public enum RateLimitFailureType {
    TIMEOUT,
    CONNECTION,
    INVALID_STATE,
    INVALID_RESULT,
    SCRIPT,
    SERIALIZATION
}
