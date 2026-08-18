package com.philia.flashsale.payment.observability;

import com.philia.flashsale.payment.payment.application.model.recovery.ReconcilePaymentResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/** Low-cardinality recovery metrics; no payment/provider identifiers are metric labels. */
public final class PaymentRecoveryObservability {

    private final MeterRegistry registry;
    private final AtomicInteger queueSize = new AtomicInteger();

    public PaymentRecoveryObservability(MeterRegistry registry) {
        this.registry = registry;
        if (registry != null) {
            Gauge.builder("payment.recovery.queue.size", queueSize, AtomicInteger::get)
                    .description("Eligible recovery work observed by the worker")
                    .register(registry);
        }
    }

    public static PaymentRecoveryObservability noop() {
        return new PaymentRecoveryObservability(null);
    }

    public void recordOutcome(String workType, ReconcilePaymentResult.Outcome outcome) {
        if (registry == null) {
            return;
        }
        Counter.builder("payment.recovery.outcomes.total")
                .tag("work_type", safeWorkType(workType))
                .tag("outcome", safeOutcome(outcome))
                .register(registry)
                .increment();
    }

    public void recordAttempt(String workType, int attemptCount) {
        if (registry == null) {
            return;
        }
        Counter.builder("payment.recovery.attempts.total")
                .tag("work_type", safeWorkType(workType))
                .register(registry)
                .increment(Math.max(1, attemptCount));
    }

    public void recordAge(String workType, Duration age) {
        if (registry == null || age == null || age.isNegative()) {
            return;
        }
        Timer.builder("payment.recovery.work.age")
                .tag("work_type", safeWorkType(workType))
                .register(registry)
                .record(age);
    }

    public void recordQueueSize(int size) {
        queueSize.set(Math.max(0, size));
    }

    private String safeWorkType(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "CREATE_SESSION", "REFRESH_SESSION", "EXPIRE_SESSION" -> value.toLowerCase(Locale.ROOT);
            default -> "unknown";
        };
    }

    private String safeOutcome(ReconcilePaymentResult.Outcome outcome) {
        return outcome == null ? "unknown" : outcome.name().toLowerCase(Locale.ROOT);
    }
}
