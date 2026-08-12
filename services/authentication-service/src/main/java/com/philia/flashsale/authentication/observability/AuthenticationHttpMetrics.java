package com.philia.flashsale.authentication.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Records bounded operation/status metrics at the HTTP adapter boundary. */
@Component
@ConditionalOnBean(MeterRegistry.class)
/** Bounded HTTP timing metrics owned by the web adapter. */
public class AuthenticationHttpMetrics {
    private final MeterRegistry registry;

    public AuthenticationHttpMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample start() { return Timer.start(registry); }

    public void stop(Timer.Sample sample, String operation, int status) {
        String safeOperation = switch (operation) {
            case "register", "login", "refresh", "logout", "logout_all" -> operation;
            default -> "other";
        };
        sample.stop(registry.timer("authentication.http.duration", "operation", safeOperation,
                "status", String.valueOf(status)));
    }
}
