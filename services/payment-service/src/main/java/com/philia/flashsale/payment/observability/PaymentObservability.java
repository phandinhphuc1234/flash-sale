package com.philia.flashsale.payment.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Payment-owned telemetry facade with a deliberately small label vocabulary.
 *
 * <p>Callers provide categories such as {@code success}, {@code timeout}, or {@code stripe}; raw
 * provider messages, IDs, URLs, secrets, and request bodies are never accepted as labels.
 */
public final class PaymentObservability {
    private static final Pattern SAFE_CATEGORY = Pattern.compile("[a-z0-9_.-]{1,32}");
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "accepted", "available", "configured", "created", "deferred", "disabled", "down",
            "error", "failed", "failure", "ignored", "invalid", "manual_review", "misconfigured",
            "ok", "processed", "recovering", "replayed", "success", "timeout", "unknown", "up");
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "authorization", "cookie", "secret", "token", "password", "signature",
            "checkout_url", "url", "raw_body", "card", "customer");

    private final MeterRegistry registry;
    private final AtomicLong outboxLagSeconds = new AtomicLong();

    public PaymentObservability(MeterRegistry registry) {
        this.registry = registry;
        if (registry != null) {
            io.micrometer.core.instrument.Gauge.builder("payment.outbox.lag.seconds", outboxLagSeconds,
                            AtomicLong::get)
                    .description("Age of the oldest pending Payment outbox event")
                    .register(registry);
        }
    }

    public void recordOutcome(String metric, String outcome) {
        if (registry == null) {
            return;
        }
        Counter.builder(metric).tag("outcome", safeCategory(outcome)).register(registry).increment();
    }

    public void recordOperation(String operation, String outcome, Duration duration) {
        if (registry == null) {
            return;
        }
        Timer.builder(operation).tag("outcome", safeCategory(outcome)).register(registry)
                .record(duration == null || duration.isNegative() ? Duration.ZERO : duration);
    }

    public Timer.Sample start() {
        return registry == null ? null : Timer.start(registry);
    }

    public void stop(Timer.Sample sample, String operation, String outcome) {
        if (sample != null && registry != null) {
            sample.stop(Timer.builder(operation).tag("outcome", safeCategory(outcome))
                    .register(registry));
        }
    }

    public void recordOutboxLag(Duration age) {
        if (age != null && !age.isNegative()) {
            outboxLagSeconds.set(age.toSeconds());
        }
    }

    public static String safeCategory(String value) {
        if (value == null) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return SAFE_CATEGORY.matcher(normalized).matches() && ALLOWED_CATEGORIES.contains(normalized)
                ? normalized : "other";
    }

    public static String safeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "absent";
        }
        return value.length() <= 64 && value.matches("[A-Za-z0-9._:-]+") ? value : "redacted";
    }

    public static String redact(String key, String value) {
        if (key != null && SENSITIVE_KEYS.contains(key.trim().toLowerCase(Locale.ROOT))) {
            return "[REDACTED]";
        }
        return value == null ? null : (value.length() <= 128 ? value : value.substring(0, 128) + "…");
    }

    public static String redactException(Throwable exception) {
        if (exception == null) {
            return "unknown";
        }
        return safeCategory(exception.getClass().getSimpleName());
    }
}
