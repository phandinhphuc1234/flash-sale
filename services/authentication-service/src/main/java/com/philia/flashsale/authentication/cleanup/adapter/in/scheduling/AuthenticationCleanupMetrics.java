package com.philia.flashsale.authentication.cleanup.adapter.in.scheduling;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Bounded cleanup counters; no user or token values are emitted. */
@Component
@ConditionalOnBean(MeterRegistry.class)
/** Bounded operational counters for scheduled retention work. */
public class AuthenticationCleanupMetrics {
    private final Counter runs;
    private final Counter failures;

    public AuthenticationCleanupMetrics(MeterRegistry registry) {
        runs = registry.counter("authentication.cleanup.runs");
        failures = registry.counter("authentication.cleanup.failures");
    }

    public void run() { runs.increment(); }
    public void failure() { failures.increment(); }
}
