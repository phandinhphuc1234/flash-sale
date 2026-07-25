package com.philia.flashsale.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class HmacRateLimitBucketKeyFactoryTests {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void createsStableOpaqueRedisKeysWithoutPlaintextIdentity() {
        HmacRateLimitBucketKeyFactory factory = new HmacRateLimitBucketKeyFactory("rl", "local", SECRET);
        byte[] ip = new byte[] {(byte) 203, 0, 113, 9};

        String first = factory.bucketKey(policy("p1", "public-catalog-read"), ip);
        String second = factory.bucketKey(policy("p1", "public-catalog-read"), ip);

        assertThat(first).isEqualTo(second);
        assertThat(first).matches("rl:k1:[0-9a-f]{64}");
        assertThat(first).doesNotContain("203").doesNotContain("public-catalog-read").doesNotContain("local");
        assertThat(factory.toString()).doesNotContain("0123456789abcdef");
    }

    @Test
    void separatesEnvironmentPolicyStateAndSecretNamespaces() {
        byte[] ip = new byte[] {(byte) 203, 0, 113, 9};

        String local = new HmacRateLimitBucketKeyFactory("rl", "local", SECRET)
                .bucketKey(policy("p1", "public-catalog-read"), ip);
        String docker = new HmacRateLimitBucketKeyFactory("rl", "docker", SECRET)
                .bucketKey(policy("p1", "public-catalog-read"), ip);
        String p2 = new HmacRateLimitBucketKeyFactory("rl", "local", SECRET)
                .bucketKey(policy("p2", "public-catalog-read"), ip);
        String changedSecret = new HmacRateLimitBucketKeyFactory(
                        "rl",
                        "local",
                        "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8))
                .bucketKey(policy("p1", "public-catalog-read"), ip);

        assertThat(Set.of(local, docker, p2, changedSecret)).hasSize(4);
    }

    @Test
    void derivesKeysSafelyAcrossConcurrentCalls() {
        HmacRateLimitBucketKeyFactory factory = new HmacRateLimitBucketKeyFactory("rl", "local", SECRET);
        byte[] ip = new byte[] {(byte) 203, 0, 113, 9};

        assertThat(IntStream.range(0, 64)
                        .parallel()
                        .mapToObj(ignored -> factory.bucketKey(policy("p1", "public-catalog-read"), ip))
                        .distinct())
                .containsExactly(factory.bucketKey(policy("p1", "public-catalog-read"), ip));
    }

    private static RateLimitPolicy policy(String stateVersion, String id) {
        return RateLimitPolicy.create(
                id,
                stateVersion,
                "product-catalog",
                Set.of(HttpMethod.GET),
                "CLIENT_IP",
                60,
                30,
                Duration.ofSeconds(1),
                1,
                "ALLOW_WITH_METRIC",
                true);
    }
}
