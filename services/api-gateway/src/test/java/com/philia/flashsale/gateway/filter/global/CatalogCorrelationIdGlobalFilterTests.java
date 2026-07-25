package com.philia.flashsale.gateway.filter.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.gateway.observability.GatewayTraceIdResolver;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class CatalogCorrelationIdGlobalFilterTests {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);

    private final GatewayTraceIdResolver traceIdResolver = new GatewayTraceIdResolver();
    private final CatalogCorrelationIdGlobalFilter filter = new CatalogCorrelationIdGlobalFilter(traceIdResolver);

    @Test
    void runsImmediatelyBeforeTheLimiterFilterSlot() {
        assertThat(filter.getOrder()).isEqualTo(RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 2);
    }

    @Test
    void propagatesNormalizedCallerTraceIdForMatchedCatalogGet() {
        MockServerWebExchange exchange = catalogExchange(HttpMethod.GET, "  trace-public-catalog  ");
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block(TEST_TIMEOUT);

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Trace-Id"))
                .isEqualTo("trace-public-catalog");
        assertThat(traceIdResolver.resolve(forwarded.get())).isEqualTo("trace-public-catalog");
    }

    @Test
    void generatesOneCachedTraceForMissingOrInvalidMatchedCatalogGet() {
        MockServerWebExchange exchange = catalogExchange(HttpMethod.GET, " ".repeat(4));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block(TEST_TIMEOUT);

        String headerTraceId = forwarded.get().getRequest().getHeaders().getFirst("X-Trace-Id");
        assertThat(headerTraceId).isNotBlank();
        assertThat(traceIdResolver.resolve(forwarded.get())).isEqualTo(headerTraceId);
    }

    @Test
    void doesNotRelaxStrictAdminCatalogTraceValidation() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/admin/catalog/products", "   ");
        setRoute(exchange, "product-catalog-admin");
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block(TEST_TIMEOUT);

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Trace-Id")).isBlank();
    }

    @Test
    void doesNotActivateFromRawPathWithoutMatchedGatewayRoute() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/catalog/products", null);
        AtomicInteger chainCalls = new AtomicInteger();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            chainCalls.incrementAndGet();
            forwarded.set(next);
            return Mono.empty();
        }).block(TEST_TIMEOUT);

        assertThat(chainCalls).hasValue(1);
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-Trace-Id")).isFalse();
    }

    private static MockServerWebExchange catalogExchange(HttpMethod method, String traceId) {
        MockServerWebExchange exchange = exchange(method, "/api/v1/catalog/products?page=0&size=20", traceId);
        setRoute(exchange, "product-catalog");
        return exchange;
    }

    private static MockServerWebExchange exchange(HttpMethod method, String path, String traceId) {
        MockServerHttpRequest.BaseBuilder<?> requestBuilder = MockServerHttpRequest.method(method, path);
        if (traceId != null) {
            requestBuilder.header("X-Trace-Id", traceId);
        }
        return MockServerWebExchange.from(requestBuilder.build());
    }

    private static void setRoute(ServerWebExchange exchange, String routeId) {
        Route route = Route.async()
                .id(routeId)
                .uri("http://product-service")
                .predicate(ignored -> true)
                .build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
    }
}
