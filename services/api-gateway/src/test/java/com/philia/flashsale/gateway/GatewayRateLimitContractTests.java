package com.philia.flashsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.gateway.error.GatewayHttpErrorWriter;
import com.philia.flashsale.gateway.filter.global.CatalogCorrelationIdGlobalFilter;
import com.philia.flashsale.gateway.filter.global.GatewayRateLimitGlobalFilter;
import com.philia.flashsale.gateway.observability.GatewayErrorObservation;
import com.philia.flashsale.gateway.observability.GatewayTraceIdResolver;
import com.philia.flashsale.gateway.ratelimit.DistributedRateLimiter;
import com.philia.flashsale.gateway.ratelimit.RateLimitDecision;
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
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class GatewayRateLimitContractTests {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);

    private final GatewayErrorObservation errorObservation = mock(GatewayErrorObservation.class);
    private final GatewayTraceIdResolver traceIdResolver = new GatewayTraceIdResolver();

    @Test
    void rejectedCatalogRequestReturnsOwned429AndCallsDownstreamZeroTimes() {
        GatewayRateLimitGlobalFilter filter = new GatewayRateLimitGlobalFilter(
                new RateLimitPolicyResolver(List.of(policy())),
                (exchange, policy) -> Optional.of(new RateLimitIdentityResolver.ResolvedRateLimitIdentity(
                        "rl:k1:test-contract-bucket")),
                rejectedLimiter(Duration.ofMillis(1_201)),
                new GatewayHttpErrorWriter(
                        new ObjectMapper(),
                        traceIdResolver,
                        errorObservation));
        MockServerWebExchange exchange = catalogExchange("  trace-contract-429  ");
        AtomicInteger downstreamCalls = new AtomicInteger();

        filter.filter(exchange, next -> {
            downstreamCalls.incrementAndGet();
            return Mono.empty();
        }).block(TEST_TIMEOUT);

        assertThat(downstreamCalls).hasValue(0);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("2");
        assertThat(exchange.getResponse().getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
        assertNoForbiddenRateLimitAccountingHeaders(exchange);
        String body = exchange.getResponse().getBodyAsString().block(TEST_TIMEOUT);
        assertThat(body).contains("\"errorCode\":\"RATE_LIMIT_EXCEEDED\"")
                .doesNotContain("traceId");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id"))
                .isEqualTo("trace-contract-429");
        verifyNoInteractions(errorObservation);
    }

    @Test
    void allowedCatalogRequestForwardsOnceWithTheCorrelationHeaderEstablishedBeforeLimiting() {
        CatalogCorrelationIdGlobalFilter correlationFilter = new CatalogCorrelationIdGlobalFilter(traceIdResolver);
        GatewayRateLimitGlobalFilter limiterFilter = enabledTestLimiterFilter(
                (policy, bucketKey) -> Mono.just(RateLimitDecision.allowed(59_000L)));
        MockServerWebExchange exchange = catalogExchange("  trace-contract-allowed  ");
        AtomicInteger downstreamCalls = new AtomicInteger();
        AtomicReference<String> forwardedTraceId = new AtomicReference<>();

        correlationFilter.filter(exchange, correlatedExchange -> limiterFilter.filter(correlatedExchange, downstream -> {
            downstreamCalls.incrementAndGet();
            forwardedTraceId.set(downstream.getRequest().getHeaders().getFirst("X-Trace-Id"));
            return Mono.empty();
        })).block(TEST_TIMEOUT);

        assertThat(downstreamCalls).hasValue(1);
        assertThat(forwardedTraceId).hasValue("trace-contract-allowed");
        assertThat(exchange.getResponse().getHeaders().containsKey("Retry-After")).isFalse();
        assertNoForbiddenRateLimitAccountingHeaders(exchange);
    }

    @Test
    void rejectedCatalogRequestUsesTheGeneratedCorrelationValueEstablishedBeforeLimiting() throws Exception {
        CatalogCorrelationIdGlobalFilter correlationFilter = new CatalogCorrelationIdGlobalFilter(traceIdResolver);
        GatewayRateLimitGlobalFilter limiterFilter = enabledTestLimiterFilter(rejectedLimiter(Duration.ofMillis(1)));
        MockServerWebExchange exchange = catalogExchange("   ");
        AtomicInteger downstreamCalls = new AtomicInteger();

        correlationFilter.filter(exchange, correlatedExchange -> limiterFilter.filter(correlatedExchange, downstream -> {
            downstreamCalls.incrementAndGet();
            return Mono.empty();
        })).block(TEST_TIMEOUT);

        assertThat(downstreamCalls).hasValue(0);
        String body = exchange.getResponse().getBodyAsString().block(TEST_TIMEOUT);
        String responseTraceId = exchange.getResponse().getHeaders().getFirst("X-Trace-Id");
        assertThat(responseTraceId).isNotBlank();
        assertThat(new ObjectMapper().readTree(body).has("traceId")).isFalse();
        assertThat(traceIdResolver.resolve(exchange)).isEqualTo(responseTraceId);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("1");
        assertNoForbiddenRateLimitAccountingHeaders(exchange);
        verifyNoInteractions(errorObservation);
    }

    private static DistributedRateLimiter rejectedLimiter(Duration retryAfter) {
        return (policy, bucketKey) -> Mono.just(RateLimitDecision.rejected(0L, retryAfter));
    }

    private GatewayRateLimitGlobalFilter enabledTestLimiterFilter(DistributedRateLimiter limiter) {
        return new GatewayRateLimitGlobalFilter(
                new RateLimitPolicyResolver(List.of(policy())),
                (exchange, policy) -> Optional.of(new RateLimitIdentityResolver.ResolvedRateLimitIdentity(
                        "rl:k1:test-contract-bucket")),
                limiter,
                new GatewayHttpErrorWriter(
                        new ObjectMapper(),
                        traceIdResolver,
                        errorObservation));
    }

    private static MockServerWebExchange catalogExchange(String traceId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/products?page=0&size=20")
                        .header("X-Trace-Id", traceId)
                        .build());
        Route route = Route.async()
                .id("product-catalog")
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

    private static void assertNoForbiddenRateLimitAccountingHeaders(MockServerWebExchange exchange) {
        assertThat(exchange.getResponse().getHeaders().containsKey("RateLimit")).isFalse();
        assertThat(exchange.getResponse().getHeaders().containsKey("RateLimit-Policy")).isFalse();
        assertThat(exchange.getResponse().getHeaders().containsKey("RateLimit-Limit")).isFalse();
        assertThat(exchange.getResponse().getHeaders().containsKey("RateLimit-Remaining")).isFalse();
        assertThat(exchange.getResponse().getHeaders().containsKey("RateLimit-Reset")).isFalse();
        assertThat(exchange.getResponse().getHeaders().keySet())
                .noneMatch(name -> name.regionMatches(
                        true,
                        0,
                        "X-RateLimit-",
                        0,
                        "X-RateLimit-".length()));
    }
}
