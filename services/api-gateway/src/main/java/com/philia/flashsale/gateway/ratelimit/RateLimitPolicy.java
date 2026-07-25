package com.philia.flashsale.gateway.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpMethod;

/**
 * Immutable rate-limit policy after configuration has been validated.
 *
 * <p>This class is still only the policy model. It does not talk to Redis and
 * does not enforce limits on requests.
 */
public final class RateLimitPolicy {

    public static final long LUA_MAX_SAFE_INTEGER = 9_007_199_254_740_991L; // 2^53 - 1
    private static final long MIN_STATE_TTL_MS = 60_000L;
    private static final long MAX_FULL_REFILL_MS = 86_400_000L;
    private static final long MAX_STATE_TTL_MS = 86_400_000L;

    private final String id;
    private final String stateVersion;
    private final String routeId;
    private final Set<HttpMethod> methods;
    private final String identityStrategy;
    private final long capacity;
    private final long refillTokens;
    private final long refillPeriodMs;
    private final long requestCost;
    private final String failureMode;
    private final boolean enabled;
    private final long capacityCredit;
    private final long requestCostCredit;
    private final long fullRefillMs;
    private final long stateTtlMs;
    private final List<String> luaArguments;

    private RateLimitPolicy(
            String id,
            String stateVersion,
            String routeId,
            Set<HttpMethod> methods,
            String identityStrategy,
            long capacity,
            long refillTokens,
            long refillPeriodMs,
            long requestCost,
            String failureMode,
            boolean enabled,
            long capacityCredit,
            long requestCostCredit,
            long fullRefillMs,
            long stateTtlMs,
            List<String> luaArguments) {
        this.id = requireText(id, "policy id");
        this.stateVersion = requireText(stateVersion, "state version");
        this.routeId = requireText(routeId, "route id");
        this.methods = copyMethods(methods);
        this.identityStrategy = requireText(identityStrategy, "identity strategy");
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodMs = refillPeriodMs;
        this.requestCost = requestCost;
        this.failureMode = requireText(failureMode, "failure mode");
        this.enabled = enabled;
        this.capacityCredit = capacityCredit;
        this.requestCostCredit = requestCostCredit;
        this.fullRefillMs = fullRefillMs;
        this.stateTtlMs = stateTtlMs;
        this.luaArguments = List.copyOf(Objects.requireNonNull(luaArguments, "lua arguments must not be null"));
    }

    public static RateLimitPolicy create(
            String id,
            String stateVersion,
            String routeId,
            Set<HttpMethod> methods,
            String identityStrategy,
            long capacity,
            long refillTokens,
            Duration refillPeriod,
            long requestCost,
            String failureMode,
            boolean enabled) {
        long refillPeriodMs = toPositiveWholeMilliseconds(refillPeriod);
        validatePositiveLuaArgument("capacity", capacity);
        validatePositiveLuaArgument("refillTokens", refillTokens);
        validatePositiveLuaArgument("refillPeriodMs", refillPeriodMs);
        validatePositiveLuaArgument("requestCost", requestCost);

        if (requestCost > capacity) {
            throw new IllegalArgumentException("request cost must not be greater than capacity");
        }

        long capacityCredit = multiplyLuaArgument("capacityCredit", capacity, refillPeriodMs);
        long requestCostCredit = multiplyLuaArgument("requestCostCredit", requestCost, refillPeriodMs);
        long fullRefillMs = ceilDivLuaArgument("fullRefillMs", capacityCredit, refillTokens);

        if (fullRefillMs > MAX_FULL_REFILL_MS) {
            throw new IllegalArgumentException("full refill duration must not be greater than 24 hours");
        }

        long stateTtlMs = Math.min(MAX_STATE_TTL_MS, Math.max(MIN_STATE_TTL_MS, Math.multiplyExact(fullRefillMs, 2L)));
        validateLuaArgument("stateTtlMs", stateTtlMs);

        List<String> luaArguments = List.of(
                Long.toString(capacityCredit),
                Long.toString(refillTokens),
                Long.toString(requestCostCredit),
                Long.toString(fullRefillMs),
                Long.toString(stateTtlMs));

        return new RateLimitPolicy(
                id,
                stateVersion,
                routeId,
                methods,
                identityStrategy,
                capacity,
                refillTokens,
                refillPeriodMs,
                requestCost,
                failureMode,
                enabled,
                capacityCredit,
                requestCostCredit,
                fullRefillMs,
                stateTtlMs,
                luaArguments);
    }

    public String id() {
        return id;
    }

    public String stateVersion() {
        return stateVersion;
    }

    public String routeId() {
        return routeId;
    }

    public Set<HttpMethod> methods() {
        return methods;
    }

    public String identityStrategy() {
        return identityStrategy;
    }

    public long capacity() {
        return capacity;
    }

    public long refillTokens() {
        return refillTokens;
    }

    public long refillPeriodMs() {
        return refillPeriodMs;
    }

    public long requestCost() {
        return requestCost;
    }

    public String failureMode() {
        return failureMode;
    }

    public boolean enabled() {
        return enabled;
    }

    public long capacityCredit() {
        return capacityCredit;
    }

    public long requestCostCredit() {
        return requestCostCredit;
    }

    public long fullRefillMs() {
        return fullRefillMs;
    }

    public long stateTtlMs() {
        return stateTtlMs;
    }

    public List<String> luaArguments() {
        return luaArguments;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static Set<HttpMethod> copyMethods(Set<HttpMethod> methods) {
        Set<HttpMethod> copied = Set.copyOf(Objects.requireNonNull(methods, "methods must not be null"));
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("methods must not be empty");
        }
        return copied;
    }

    private static long toPositiveWholeMilliseconds(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("refill period must be positive");
        }
        long nanos = duration.toNanos();
        if (nanos % 1_000_000L != 0) {
            throw new IllegalArgumentException("refill period must be expressed in whole milliseconds");
        }
        return duration.toMillis();
    }

    private static void validatePositiveLuaArgument(String name, long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        validateLuaArgument(name, value);
    }

    private static long multiplyLuaArgument(String name, long left, long right) {
        try {
            long value = Math.multiplyExact(left, right);
            validateLuaArgument(name, value);
            return value;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " exceeds Lua safe integer limit", exception);
        }
    }

    private static long ceilDivLuaArgument(String name, long numerator, long denominator) {
        long value = Math.ceilDiv(numerator, denominator);
        validateLuaArgument(name, value);
        return value;
    }

    private static void validateLuaArgument(String name, long value) {
        if (value > LUA_MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException(name + " must not exceed 2^53 - 1");
        }
    }
}
