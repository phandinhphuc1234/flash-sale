package com.philia.flashsale.gateway.filter.global;

import com.philia.flashsale.gateway.observability.GatewayTraceIdResolver;
import java.util.Objects;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Normalizes public catalog request correlation before rate-limit evaluation.
 *
 * <p>Admin catalog validation remains owned by {@link AdminCatalogRequestBoundaryFilter};
 * this filter only applies after the public catalog Gateway route is selected.
 */
@Component
public final class CatalogCorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    private static final String PUBLIC_CATALOG_ROUTE_ID = "product-catalog";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final GatewayTraceIdResolver traceIdResolver;

    public CatalogCorrelationIdGlobalFilter(GatewayTraceIdResolver traceIdResolver) {
        this.traceIdResolver = Objects.requireNonNull(traceIdResolver, "trace id resolver must not be null");
    }

    @Override
    public int getOrder() {
        return RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!matchesPublicCatalogGet(exchange)) {
            return chain.filter(exchange);
        }

        String traceId = traceIdResolver.resolve(exchange);
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(TRACE_ID_HEADER, traceId))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private static boolean matchesPublicCatalogGet(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null
                && PUBLIC_CATALOG_ROUTE_ID.equals(route.getId())
                && HttpMethod.GET.equals(exchange.getRequest().getMethod());
    }
}
