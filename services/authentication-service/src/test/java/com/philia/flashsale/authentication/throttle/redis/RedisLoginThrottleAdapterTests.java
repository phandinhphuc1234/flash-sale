package com.philia.flashsale.authentication.throttle.redis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Base64;

import com.philia.flashsale.authentication.configuration.AuthenticationProperties;
import com.philia.flashsale.authentication.session.application.login.LoginRateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisLoginThrottleAdapterTests {

    @Test
    void activeCooldownCarriesThePositiveRedisTtlToTheHttpBoundary() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<LoginThrottleMetrics> metrics = mock(ObjectProvider.class);
        when(redis.getExpire(anyString())).thenReturn(73L);
        RedisLoginThrottleAdapter adapter = new RedisLoginThrottleAdapter(redis, properties(), metrics);

        assertThatThrownBy(() -> adapter.beforeAttempt("user@example.test"))
                .isInstanceOf(LoginRateLimitExceededException.class)
                .extracting(error -> ((LoginRateLimitExceededException) error).retryAfterSeconds())
                .isEqualTo(73L);
    }

    private AuthenticationProperties properties() {
        String hmacSecret = Base64.getEncoder().encodeToString(new byte[32]);
        return new AuthenticationProperties(
                new AuthenticationProperties.Cookie(false, "Lax", "/api/v1/auth"),
                "https://app.example",
                new AuthenticationProperties.Throttle(hmacSecret, Duration.ofMinutes(15), Duration.ofMinutes(15), 5),
                new AuthenticationProperties.Retention(30),
                "0 0 3 * * *",
                new AuthenticationProperties.Argon2(16, 32, 1, 19_456, 2));
    }
}
