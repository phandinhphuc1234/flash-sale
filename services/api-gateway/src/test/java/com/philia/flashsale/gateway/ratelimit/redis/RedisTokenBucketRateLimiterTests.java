package com.philia.flashsale.gateway.ratelimit.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.philia.flashsale.gateway.ratelimit.RateLimitCoordinatorException;
import com.philia.flashsale.gateway.ratelimit.RateLimitDecision;
import com.philia.flashsale.gateway.ratelimit.RateLimitFailureType;
import com.philia.flashsale.gateway.ratelimit.RateLimitPolicy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;

@Testcontainers
class RedisTokenBucketRateLimiterTests {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(2);
    private static final String BUCKET_KEY = "rl:k1:test-catalog-bucket";
    private static final long LUA_MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;

    private ReactiveStringRedisTemplate redisTemplate;
    private RedisTokenBucketRateLimiter limiter;

    @BeforeAll
    static void startRedisConnectionFactory() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedisConnectionFactory() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void resetRedis() throws Exception {
        REDIS.execInContainer("redis-cli", "FLUSHDB");
        redisTemplate = new ReactiveStringRedisTemplate(connectionFactory);
        limiter = new RedisTokenBucketRateLimiter(redisTemplate, TEST_TIMEOUT);
    }

    @Test
    void missingStateStartsFullAndPersistsCanonicalStateWithTtl() {
        RateLimitPolicy policy = mvpPolicy();

        RateLimitDecision decision = acquire(policy, BUCKET_KEY);

        assertThat(decision.outcome()).isEqualTo(RateLimitDecision.Outcome.ALLOWED);
        assertThat(decision.remainingCredit()).isEqualTo(59_000L);
        assertThat(decision.retryAfter()).isNull();

        Map<String, String> state = redisHash(BUCKET_KEY);
        assertThat(state).containsEntry("credit", "59000");
        assertThat(state.get("last_refill_ms")).matches("[1-9][0-9]*");
        assertThat(redisTtl(BUCKET_KEY)).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void allowedAcquisitionRefillsToCapacityClampAndWritesNonExponentDecimals() throws Exception {
        RateLimitPolicy policy = mvpPolicy();

        acquire(policy, BUCKET_KEY);
        TimeUnit.MILLISECONDS.sleep(50L);
        RateLimitDecision secondDecision = acquire(policy, BUCKET_KEY);

        assertThat(secondDecision.allowed()).isTrue();
        assertThat(secondDecision.remainingCredit()).isEqualTo(59_000L);

        Map<String, String> state = redisHash(BUCKET_KEY);
        assertThat(state.get("credit")).isEqualTo("59000");
        assertThat(state.get("credit")).doesNotContainIgnoringCase("e");
        assertThat(state.get("last_refill_ms")).matches("[1-9][0-9]*").doesNotContainIgnoringCase("e");
    }

    @Test
    void rejectedAcquisitionDoesNotRefreshTtlAndReturnsPositiveRetryDelay() {
        RateLimitPolicy policy = policy(1, 1, Duration.ofSeconds(1), 1);

        RateLimitDecision allowed = acquire(policy, BUCKET_KEY);
        Duration ttlBeforeRejection = redisTtl(BUCKET_KEY);
        RateLimitDecision rejected = acquire(policy, BUCKET_KEY);
        Duration ttlAfterRejection = redisTtl(BUCKET_KEY);

        assertThat(allowed.allowed()).isTrue();
        assertThat(rejected.rejected()).isTrue();
        assertThat(rejected.remainingCredit()).isGreaterThanOrEqualTo(0L).isLessThan(1_000L);
        assertThat(rejected.retryAfter()).isPositive();
        assertThat(ttlAfterRejection).isLessThanOrEqualTo(ttlBeforeRejection);
    }

    @Test
    void scriptCacheFlushRecoversAndKeepsDecisionContractStable() throws Exception {
        RateLimitPolicy policy = mvpPolicy();

        RateLimitDecision firstDecision = acquire(policy, "rl:k1:script-cache-a");
        REDIS.execInContainer("redis-cli", "SCRIPT", "FLUSH");
        RateLimitDecision secondDecision = acquire(policy, "rl:k1:script-cache-b");

        assertThat(firstDecision.allowed()).isTrue();
        assertThat(secondDecision.allowed()).isTrue();
        assertThat(redisHash("rl:k1:script-cache-b")).containsEntry("credit", "59000");
    }

    @Test
    void storedNonCanonicalNumbersAreRejectedWithoutMutatingState() {
        RateLimitPolicy policy = mvpPolicy();
        seedHash(BUCKET_KEY, Map.of("credit", "01", "last_refill_ms", "1"), Duration.ofSeconds(60));

        Throwable error = catchThrowable(() -> limiter.acquire(policy, BUCKET_KEY).block(TEST_TIMEOUT));

        assertThat(error).isInstanceOf(RateLimitCoordinatorException.class);
        RateLimitCoordinatorException exception = (RateLimitCoordinatorException) error;
        assertThat(exception.failureType()).isEqualTo(RateLimitFailureType.INVALID_STATE);

        assertThat(redisHash(BUCKET_KEY)).containsEntry("credit", "01").containsEntry("last_refill_ms", "1");
        assertThat(redisTtl(BUCKET_KEY)).isPositive();
    }

    @Test
    void largestSafeLuaIntegerRoundTripsWithoutScientificNotation() {
        RateLimitPolicy policy = policy(LUA_MAX_SAFE_INTEGER, LUA_MAX_SAFE_INTEGER, Duration.ofMillis(1), 1);

        RateLimitDecision decision = acquire(policy, BUCKET_KEY);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remainingCredit()).isEqualTo(LUA_MAX_SAFE_INTEGER - 1L);

        Map<String, String> state = redisHash(BUCKET_KEY);
        assertThat(state.get("credit")).isEqualTo(Long.toString(LUA_MAX_SAFE_INTEGER - 1L));
        assertThat(state.get("credit")).doesNotContainIgnoringCase("e");
        assertThat(state.get("last_refill_ms")).matches("[1-9][0-9]*").doesNotContainIgnoringCase("e");
    }

    @Test
    void concurrentAcquisitionsAcrossThreeClientsDoNotOverspendTheSharedBucket() {
        RateLimitPolicy policy = policy(20, 1, Duration.ofMinutes(72), 1);
        List<RedisTokenBucketRateLimiter> clients = IntStream.range(0, 3)
                .mapToObj(ignored -> new RedisTokenBucketRateLimiter(redisTemplate, TEST_TIMEOUT))
                .toList();

        List<RateLimitDecision> decisions = Flux.range(0, 100)
                .flatMap(request -> clients.get(request % clients.size()).acquire(policy, BUCKET_KEY), 100)
                .cast(RateLimitDecision.class)
                .collectList()
                .block(TEST_TIMEOUT);

        assertThat(decisions).hasSize(100);
        assertThat(decisions.stream().filter(RateLimitDecision::allowed)).hasSize(20);
        assertThat(decisions.stream().filter(RateLimitDecision::rejected)).hasSize(80);
        assertThat(decisions).allMatch(decision -> decision.remainingCredit() >= 0L);
        assertThat(redisHash(BUCKET_KEY).get("credit")).isEqualTo("0");
    }

    private RateLimitDecision acquire(RateLimitPolicy policy, String bucketKey) {
        return limiter.acquire(policy, bucketKey).block(TEST_TIMEOUT);
    }

    private Map<String, String> redisHash(String bucketKey) {
        return redisTemplate.opsForHash()
                .entries(bucketKey)
                .collectMap(entry -> (String) entry.getKey(), entry -> (String) entry.getValue())
                .block(TEST_TIMEOUT);
    }

    private Duration redisTtl(String bucketKey) {
        return redisTemplate.getExpire(bucketKey).block(TEST_TIMEOUT);
    }

    private void seedHash(String bucketKey, Map<String, String> values, Duration ttl) {
        redisTemplate.opsForHash().putAll(bucketKey, values).block(TEST_TIMEOUT);
        redisTemplate.expire(bucketKey, ttl).block(TEST_TIMEOUT);
    }

    private static RateLimitPolicy mvpPolicy() {
        return policy(60, 30, Duration.ofSeconds(1), 1);
    }

    private static RateLimitPolicy policy(long capacity, long refillTokens, Duration refillPeriod, long requestCost) {
        return RateLimitPolicy.create(
                "public-catalog-read",
                "p1",
                "product-catalog",
                Set.of(HttpMethod.GET),
                "CLIENT_IP",
                capacity,
                refillTokens,
                refillPeriod,
                requestCost,
                "ALLOW_WITH_METRIC",
                true);
    }
}
