package com.philia.flashsale.gateway.filter.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.gateway.error.GatewayHttpErrorWriter;
import com.philia.flashsale.gateway.observability.GatewayErrorObservation;
import com.philia.flashsale.gateway.observability.GatewayTraceIdResolver;
import com.philia.flashsale.gateway.ratelimit.DistributedRateLimiter;
import com.philia.flashsale.gateway.ratelimit.RateLimitCoordinatorException;
import com.philia.flashsale.gateway.ratelimit.RateLimitDecision;
import com.philia.flashsale.gateway.ratelimit.RateLimitFailureType;
import com.philia.flashsale.gateway.ratelimit.RateLimitIdentityResolver;
import com.philia.flashsale.gateway.ratelimit.RateLimitPolicy;
import com.philia.flashsale.gateway.ratelimit.RateLimitPolicyResolver;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class GatewayRateLimitGlobalFilterTests {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);
    private static final String TEST_BUCKET_KEY = "rl:k1:test-filter-bucket";

    @Test
    void runsImmediatelyBeforeGatewayRouting() {
        GatewayRateLimitGlobalFilter filter = filter(RateLimitDecision.allowed(59_000L));

        assertThat(filter.getOrder()).isEqualTo(RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 1);
    }

    @Test
    void bypassesUnmatchedRouteWithoutResolvingIdentityOrQuota() {
        CapturingIdentityResolver identityResolver = new CapturingIdentityResolver(Optional.of(TEST_BUCKET_KEY));
        CapturingLimiter limiter = new CapturingLimiter(RateLimitDecision.allowed(59_000L));
        GatewayRateLimitGlobalFilter filter = filter(identityResolver, limiter);
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/admin/catalog/products", "product-catalog-admin");
        AtomicInteger chainCalls = new AtomicInteger();

        filter.filter(exchange, next -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        }).block(TEST_TIMEOUT);

        assertThat(chainCalls).hasValue(1);
        assertThat(identityResolver.calls()).isZero();
        assertThat(limiter.calls()).isZero();
    }

    @Test
    void bypassesUnmatchedMethodForTheCatalogRoute() {
        CapturingIdentityResolver identityResolver = new CapturingIdentityResolver(Optional.of(TEST_BUCKET_KEY));
        CapturingLimiter limiter = new CapturingLimiter(RateLimitDecision.allowed(59_000L));
        GatewayRateLimitGlobalFilter filter = filter(identityResolver, limiter);
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/api/v1/catalog/products", "product-catalog");
        AtomicInteger chainCalls = new AtomicInteger();

        filter.filter(exchange, next -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        }).block(TEST_TIMEOUT);

        assertThat(chainCalls).hasValue(1);
        assertThat(identityResolver.calls()).isZero();
        assertThat(limiter.calls()).isZero();
    }

    @Test
    void allowedDecisionInvokesDownstreamExactlyOnce() {
        CapturingIdentityResolver identityResolver = new CapturingIdentityResolver(Optional.of(TEST_BUCKET_KEY));
        CapturingLimiter limiter = new CapturingLimiter(RateLimitDecision.allowed(59_000L));
        GatewayRateLimitGlobalFilter filter = filter(identityResolver, limiter);
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/catalog/products", "product-catalog");
        AtomicInteger chainCalls = new AtomicInteger();

        filter.filter(exchange, next -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        }).block(TEST_TIMEOUT);

        assertThat(chainCalls).hasValue(1);
        assertThat(identityResolver.calls()).isEqualTo(1);
        assertThat(limiter.calls()).isEqualTo(1);
        assertThat(limiter.lastBucketKey()).isEqualTo(TEST_BUCKET_KEY);
    }

    @Test
    void rejectedDecisionStopsBeforeDownstreamAndUsesGatewayOwnedResponse() {
        CapturingIdentityResolver identityResolver = new CapturingIdentityResolver(Optional.of(TEST_BUCKET_KEY));
        CapturingLimiter limiter = new CapturingLimiter(RateLimitDecision.rejected(0L, Duration.ofSeconds(1)));
        GatewayRateLimitGlobalFilter filter = filter(identityResolver, limiter);
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/catalog/products", "product-catalog");
        AtomicInteger chainCalls = new AtomicInteger();

        filter.filter(exchange, next -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        }).block(TEST_TIMEOUT);

        assertThat(chainCalls).hasValue(0);
        assertThat(identityResolver.calls()).isEqualTo(1);
        assertThat(limiter.calls()).isEqualTo(1);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void identityUnavailableForwardsOnceWithoutQuotaHeaders() {
        GatewayRateLimitGlobalFilter filter = filter(
                new CapturingIdentityResolver(Optional.empty()),
                new CapturingLimiter(RateLimitDecision.allowed(59_000L)));
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/catalog/products", "product-catalog");
        AtomicInteger chainCalls = new AtomicInteger();

        filter.filter(exchange, next -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        }).block(TEST_TIMEOUT);

        assertThat(chainCalls).hasValue(1);
        assertThat(exchange.getResponse().getHeaders().containsKey("Retry-After")).isFalse();
    }

    @Test
    void typedCoordinatorFailureFailsOpenAndForwardsOnce() {
        CapturingIdentityResolver identityResolver = new CapturingIdentityResolver(Optional.of(TEST_BUCKET_KEY));
        GatewayRateLimitGlobalFilter filter = filter(
                identityResolver,
                (policy, bucketKey) -> Mono.error(new RateLimitCoordinatorException(
                        RateLimitFailureType.CONNECTION,
                        "redis unavailable")));
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/catalog/products", "product-catalog");
        AtomicInteger chainCalls = new AtomicInteger();

        filter.filter(exchange, next -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        }).block(TEST_TIMEOUT);

        assertThat(chainCalls).hasValue(1);
        assertThat(identityResolver.calls()).isEqualTo(1);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(exchange.getResponse().getHeaders().containsKey("Retry-After")).isFalse();
    }

    private static GatewayRateLimitGlobalFilter filter(RateLimitDecision decision) {
        return filter(new CapturingIdentityResolver(Optional.of(TEST_BUCKET_KEY)), new CapturingLimiter(decision));
    }

    private static GatewayRateLimitGlobalFilter filter(
            RateLimitIdentityResolver identityResolver,
            DistributedRateLimiter limiter) {
        return new GatewayRateLimitGlobalFilter(
                new RateLimitPolicyResolver(List.of(policy())),
                identityResolver,
                limiter,
                new GatewayHttpErrorWriter(
                        new ObjectMapper(),
                        new GatewayTraceIdResolver(),
                        new GatewayErrorObservation()));
    }

    private static MockServerWebExchange exchange(HttpMethod method, String path, String routeId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(method, path)
                        .header("X-Trace-Id", "trace-filter-test")
                        .build());
        Route route = Route.async()
                .id(routeId)
                .uri("http://product-service")
                .predicate(ignored -> true)
                .build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        return exchange;
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

    private static final class CapturingLimiter implements DistributedRateLimiter {

        private final RateLimitDecision decision;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<String> lastBucketKey = new AtomicReference<>();

        private CapturingLimiter(RateLimitDecision decision) {
            this.decision = decision;
        }

        @Override
        public Mono<RateLimitDecision> acquire(RateLimitPolicy policy, String bucketKey) {
            calls.incrementAndGet();
            lastBucketKey.set(bucketKey);
            return Mono.just(decision);
        }

        private int calls() {
            return calls.get();
        }

        private String lastBucketKey() {
            return lastBucketKey.get();
        }
    }

    private static final class CapturingIdentityResolver implements RateLimitIdentityResolver {

        private final Optional<String> bucketKey;
        private final AtomicInteger calls = new AtomicInteger();

        private CapturingIdentityResolver(Optional<String> bucketKey) {
            this.bucketKey = bucketKey;
        }

        @Override
        public Optional<ResolvedRateLimitIdentity> resolve(ServerWebExchange exchange, RateLimitPolicy policy) {
            calls.incrementAndGet();
            return bucketKey.map(ResolvedRateLimitIdentity::new);
        }

        private int calls() {
            return calls.get();
        }
    }
}
