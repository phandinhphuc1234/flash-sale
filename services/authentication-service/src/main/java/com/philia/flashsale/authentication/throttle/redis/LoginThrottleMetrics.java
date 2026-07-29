package com.philia.flashsale.authentication.throttle.redis;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Bounded Redis throttle metrics; identifier values are deliberately never used as tags. */
@Component
@ConditionalOnBean(MeterRegistry.class)
/** Bounded metrics for Redis throttle outcomes; raw identifiers are never metric tags. */
public class LoginThrottleMetrics {
    private final Counter failures;
    private final Counter unavailable;

    public LoginThrottleMetrics(MeterRegistry registry) {
        this.failures = registry.counter("authentication.throttle.failures");
        this.unavailable = registry.counter("authentication.throttle.unavailable");
    }

    public void failureRecorded() { failures.increment(); }
    public void unavailable() { unavailable.increment(); }
}
