package com.philia.flashsale.authentication.throttle.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.philia.flashsale.authentication.session.application.login.LoginThrottlePort;
import com.philia.flashsale.authentication.session.application.login.LoginThrottleUnavailableException;
import com.philia.flashsale.authentication.session.application.login.LoginRateLimitExceededException;
import com.philia.flashsale.authentication.configuration.AuthenticationProperties;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Redis-backed rolling-window throttle. WATCH/MULTI/EXEC makes the fifth-failure decision
 * contention-safe while bounded retries keep a hot login key from blocking request threads.
 */
@Component
@ConditionalOnProperty(prefix = "flashsale.authentication", name = "runtime-enabled", havingValue = "true", matchIfMissing = true)
/** Redis throttle adapter implementing cooldown and atomic rolling-window failure recording. */
public class RedisLoginThrottleAdapter implements LoginThrottlePort {
    private static final int MAX_CONTENTION_RETRIES = 3;

    private final StringRedisTemplate redis;
    private final AuthenticationProperties properties;
    private final LoginThrottleKeyFactory keyFactory;
    private final ObjectProvider<LoginThrottleMetrics> metrics;

    public RedisLoginThrottleAdapter(StringRedisTemplate redis, AuthenticationProperties properties,
            ObjectProvider<LoginThrottleMetrics> metrics) {
        this.redis = redis;
        this.properties = properties;
        this.keyFactory = new LoginThrottleKeyFactory(properties);
        this.metrics = metrics;
    }

    @Override
    public void beforeAttempt(String normalizedLogin) {
        try {
            String key = keyFactory.key("cooldown", normalizedLogin);
            Long ttl = redis.getExpire(key);
            if (ttl != null && ttl > 0) {
                throw new LoginRateLimitExceededException(ttl);
            }
        } catch (LoginRateLimitExceededException failure) {
            throw failure;
        } catch (RuntimeException exception) {
            recordUnavailable();
            throw new LoginThrottleUnavailableException("Login throttle unavailable", exception);
        }
    }

    @Override
    public void recordFailure(String normalizedLogin) {
        Duration window = properties.throttle().failureWindow();
        Instant now = Instant.now();
        String failedKey = keyFactory.key("failed", normalizedLogin);
        String cooldownKey = keyFactory.key("cooldown", normalizedLogin);

        for (int attempt = 0; attempt < MAX_CONTENTION_RETRIES; attempt++) {
            try {
                Boolean committed = redis.execute(new SessionCallback<Boolean>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <K, V> Boolean execute(RedisOperations<K, V> genericOperations) {
                        RedisOperations<String, String> operations = (RedisOperations<String, String>) genericOperations;
                        operations.watch(List.of(failedKey, cooldownKey));
                        operations.opsForZSet().removeRangeByScore(failedKey, 0,
                                now.minus(window).toEpochMilli());
                        Long existing = operations.opsForZSet().zCard(failedKey);
                        long projected = existing == null ? 1 : existing + 1;
                        operations.multi();
                        operations.opsForZSet().add(failedKey, Long.toString(System.nanoTime()), now.toEpochMilli());
                        operations.opsForZSet().removeRangeByScore(failedKey, 0,
                                now.minus(window).toEpochMilli());
                        operations.expire(failedKey, window);
                        if (projected >= properties.throttle().maxFailures()) {
                            operations.opsForValue().set(cooldownKey, "1", properties.throttle().cooldown());
                        }
                        return operations.exec() != null;
                    }
                });
                if (Boolean.TRUE.equals(committed)) {
                    recordFailureMetric();
                    return;
                }
            } catch (RuntimeException exception) {
                recordUnavailable();
                throw new LoginThrottleUnavailableException("Login throttle unavailable", exception);
            }
        }
        recordUnavailable();
        throw new LoginThrottleUnavailableException("Login throttle contention", null);
    }

    private void recordFailureMetric() {
        LoginThrottleMetrics value = metrics.getIfAvailable();
        if (value != null) value.failureRecorded();
    }

    private void recordUnavailable() {
        LoginThrottleMetrics value = metrics.getIfAvailable();
        if (value != null) value.unavailable();
    }

    @Override
    public void beforeCommit(String normalizedLogin) {
        beforeAttempt(normalizedLogin);
    }
}
