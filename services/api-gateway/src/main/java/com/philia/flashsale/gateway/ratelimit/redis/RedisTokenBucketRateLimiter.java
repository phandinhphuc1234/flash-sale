package com.philia.flashsale.gateway.ratelimit.redis;

import com.philia.flashsale.gateway.ratelimit.DistributedRateLimiter;
import com.philia.flashsale.gateway.ratelimit.RateLimitCoordinatorException;
import com.philia.flashsale.gateway.ratelimit.RateLimitDecision;
import com.philia.flashsale.gateway.ratelimit.RateLimitFailureType;
import com.philia.flashsale.gateway.ratelimit.RateLimitPolicy;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import reactor.core.publisher.Mono;

/**
 * Redis-backed token-bucket coordinator for the Gateway rate-limit boundary.
 *
 * <p>The Redis script performs the atomic state transition. This adapter owns
 * framework execution and strict tuple decoding, then returns the technology
 * independent {@link RateLimitDecision} used by Gateway filters.
 */
public final class RedisTokenBucketRateLimiter implements DistributedRateLimiter {

    private static final Pattern CANONICAL_UNSIGNED_DECIMAL = Pattern.compile("0|[1-9][0-9]*");
    private static final RedisScript<List> TOKEN_BUCKET_SCRIPT = tokenBucketScript();

    private final ReactiveStringRedisTemplate redisTemplate;
    private final Duration commandTimeout;

    public RedisTokenBucketRateLimiter(ReactiveStringRedisTemplate redisTemplate, Duration commandTimeout) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redis template must not be null");
        this.commandTimeout = requirePositive(commandTimeout);
    }

    @Override
    public Mono<RateLimitDecision> acquire(RateLimitPolicy policy, String bucketKey) {
        Objects.requireNonNull(policy, "rate-limit policy must not be null");
        if (bucketKey == null || bucketKey.isBlank()) {
            return Mono.error(new IllegalArgumentException("bucket key must not be blank"));
        }

        return redisTemplate
                .execute(TOKEN_BUCKET_SCRIPT, List.of(bucketKey), policy.luaArguments())
                .single()
                .timeout(commandTimeout)
                .map(result -> decodeTuple(result, policy))
                .onErrorMap(this::toCoordinatorException);
    }

    private Throwable toCoordinatorException(Throwable failure) {
        if (failure instanceof RateLimitCoordinatorException) {
            return failure;
        }
        if (failure instanceof TimeoutException || failure instanceof RedisCommandTimeoutException) {
            return new RateLimitCoordinatorException(
                    RateLimitFailureType.TIMEOUT,
                    "rate-limit Redis command timed out",
                    failure);
        }
        if (failure instanceof RedisConnectionFailureException || failure instanceof RedisConnectionException) {
            return new RateLimitCoordinatorException(
                    RateLimitFailureType.CONNECTION,
                    "rate-limit Redis connection failed",
                    failure);
        }
        if (failure instanceof RedisCommandExecutionException) {
            return new RateLimitCoordinatorException(
                    RateLimitFailureType.SCRIPT,
                    "rate-limit Redis script failed",
                    failure);
        }
        if (failure instanceof RedisSystemException systemException) {
            return mapImmediateRedisSystemCause(systemException);
        }
        if (failure instanceof QueryTimeoutException queryTimeoutException
                && queryTimeoutException.getCause() instanceof RedisCommandTimeoutException) {
            return new RateLimitCoordinatorException(
                    RateLimitFailureType.TIMEOUT,
                    "rate-limit Redis command timed out",
                    failure);
        }
        return failure;
    }

    private Throwable mapImmediateRedisSystemCause(RedisSystemException failure) {
        Throwable cause = failure.getCause();
        if (cause instanceof RedisCommandTimeoutException) {
            return new RateLimitCoordinatorException(
                    RateLimitFailureType.TIMEOUT,
                    "rate-limit Redis command timed out",
                    failure);
        }
        if (cause instanceof RedisConnectionException) {
            return new RateLimitCoordinatorException(
                    RateLimitFailureType.CONNECTION,
                    "rate-limit Redis connection failed",
                    failure);
        }
        if (cause instanceof RedisCommandExecutionException) {
            return new RateLimitCoordinatorException(
                    RateLimitFailureType.SCRIPT,
                    "rate-limit Redis script failed",
                    failure);
        }
        return failure;
    }

    private RateLimitDecision decodeTuple(List<?> tuple, RateLimitPolicy policy) {
        if (tuple == null || tuple.size() != 3) {
            throw invalidResult("script result must be a three-field tuple");
        }

        String status = tupleField(tuple, 0);
        String value = tupleField(tuple, 1);
        String reserved = tupleField(tuple, 2);
        if (!"0".equals(reserved)) {
            throw invalidResult("script reserved field must be zero");
        }

        return switch (status) {
            case "ALLOWED" -> RateLimitDecision.allowed(decodeRemainingCredit(value, policy.capacityCredit()));
            case "REJECTED" -> rejected(value, policy);
            case "ERROR" -> error(value);
            default -> throw invalidResult("script status is unknown");
        };
    }

    private RateLimitDecision rejected(String value, RateLimitPolicy policy) {
        long remainingCredit = decodeRemainingCredit(value, policy.capacityCredit());
        if (remainingCredit >= policy.requestCostCredit()) {
            throw invalidResult("rejected credit must be lower than request cost");
        }

        long retryAfterMs = Math.ceilDiv(policy.requestCostCredit() - remainingCredit, policy.refillTokens());
        long maxRetryAfterMs = Math.ceilDiv(policy.requestCostCredit(), policy.refillTokens());
        if (retryAfterMs < 1 || retryAfterMs > maxRetryAfterMs) {
            throw invalidResult("retry delay is outside the approved bounds");
        }

        return RateLimitDecision.rejected(remainingCredit, Duration.ofMillis(retryAfterMs));
    }

    private RateLimitDecision error(String reason) {
        switch (reason) {
            case "INVALID_STATE" -> throw new RateLimitCoordinatorException(
                    RateLimitFailureType.INVALID_STATE, "rate-limit state is invalid");
            case "INVALID_ARGUMENT" -> throw new RateLimitCoordinatorException(
                    RateLimitFailureType.SCRIPT, "rate-limit script arguments are invalid");
            default -> throw invalidResult("script error reason is unknown");
        }
    }

    private long decodeRemainingCredit(String value, long capacityCredit) {
        long remainingCredit = decodeCanonicalUnsignedDecimal(value);
        if (remainingCredit > capacityCredit) {
            throw invalidResult("remaining credit exceeds policy capacity");
        }
        return remainingCredit;
    }

    private long decodeCanonicalUnsignedDecimal(String value) {
        if (value == null || !CANONICAL_UNSIGNED_DECIMAL.matcher(value).matches()) {
            throw invalidResult("numeric script field is not canonical");
        }

        try {
            long parsed = Long.parseLong(value);
            if (parsed > RateLimitPolicy.LUA_MAX_SAFE_INTEGER) {
                throw invalidResult("numeric script field exceeds Lua safe integer limit");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalidResult("numeric script field cannot be parsed", exception);
        }
    }

    private String tupleField(List<?> tuple, int index) {
        Object value = tuple.get(index);
        if (!(value instanceof String text)) {
            throw invalidResult("script tuple field must be a string");
        }
        return text;
    }

    private static Duration requirePositive(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Redis command timeout must be positive");
        }
        return timeout;
    }

    private static RateLimitCoordinatorException invalidResult(String message) {
        return new RateLimitCoordinatorException(RateLimitFailureType.INVALID_RESULT, message);
    }

    private static RateLimitCoordinatorException invalidResult(String message, Throwable cause) {
        return new RateLimitCoordinatorException(RateLimitFailureType.INVALID_RESULT, message, cause);
    }

    private static RedisScript<List> tokenBucketScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/token_bucket.lua")));
        script.setResultType(List.class);
        return script;
    }
}
