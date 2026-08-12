package com.philia.flashsale.gateway.filter.global;

import com.philia.flashsale.gateway.observability.GatewayTraceIdResolver;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Adds the normalized trace header to Authentication requests before proxying. */
@Component
public class AuthenticationCorrelationIdGlobalFilter implements GlobalFilter, Ordered {
    private static final String TRACE_HEADER = "X-Trace-Id";

    private final GatewayTraceIdResolver traceIdResolver;

    public AuthenticationCorrelationIdGlobalFilter(GatewayTraceIdResolver traceIdResolver) {
        this.traceIdResolver = traceIdResolver;
    }

    @Override
    public int getOrder() {
        return RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null || !route.getId().startsWith("authentication-")) return chain.filter(exchange);
        String traceId = traceIdResolver.resolve(exchange);
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(TRACE_HEADER, traceId)).build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}
