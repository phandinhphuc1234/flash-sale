package com.philia.flashsale.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class DirectClientIpRateLimitIdentityResolverTests {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private final HmacRateLimitBucketKeyFactory keyFactory =
            new HmacRateLimitBucketKeyFactory("rl", "local", SECRET);
    private final DirectClientIpRateLimitIdentityResolver resolver =
            new DirectClientIpRateLimitIdentityResolver(keyFactory);

    @Test
    void resolvesDirectIpv4PeerToOpaqueBucketKeyAndIgnoresForwardingHeaders() throws Exception {
        MockServerWebExchange direct = exchange("203.0.113.9", null);
        MockServerWebExchange spoofed = exchange("203.0.113.9", "198.51.100.99");

        Optional<RateLimitIdentityResolver.ResolvedRateLimitIdentity> directIdentity =
                resolver.resolve(direct, policy());
        Optional<RateLimitIdentityResolver.ResolvedRateLimitIdentity> spoofedIdentity =
                resolver.resolve(spoofed, policy());

        assertThat(directIdentity).isPresent();
        assertThat(directIdentity.get().bucketKey()).matches("rl:k1:[0-9a-f]{64}");
        assertThat(spoofedIdentity).isPresent();
        assertThat(spoofedIdentity.get().bucketKey()).isEqualTo(directIdentity.get().bucketKey());
    }

    @Test
    void resolvesIpv6PeerToStableOpaqueBucketKey() throws Exception {
        Optional<RateLimitIdentityResolver.ResolvedRateLimitIdentity> identity =
                resolver.resolve(exchange("2001:db8::5", null), policy());

        assertThat(identity).isPresent();
        assertThat(identity.get().bucketKey()).matches("rl:k1:[0-9a-f]{64}");
    }

    @Test
    void skipsLimitingWhenDirectAddressIsUnavailable() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/products")
                        .remoteAddress(InetSocketAddress.createUnresolved("client.example", 12345))
                        .build());

        assertThat(resolver.resolve(exchange, policy())).isEmpty();
    }

    private static MockServerWebExchange exchange(String address, String forwardedFor) throws Exception {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/api/v1/catalog/products")
                .remoteAddress(new InetSocketAddress(InetAddress.getByName(address), 12345));
        if (forwardedFor != null) {
            builder.header("X-Forwarded-For", forwardedFor);
            builder.header("Forwarded", "for=" + forwardedFor);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private static RateLimitPolicy policy() {
        return RateLimitPolicy.create(
                "public-catalog-read",
                "p1",
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
