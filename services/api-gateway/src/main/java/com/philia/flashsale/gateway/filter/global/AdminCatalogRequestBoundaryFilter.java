package com.philia.flashsale.gateway.filter.global;

import com.philia.flashsale.gateway.error.GatewayErrorCode;
import com.philia.flashsale.gateway.error.GatewayHttpErrorWriter;
import com.philia.flashsale.gateway.observability.GatewayTraceIdResolver;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Enforces edge-owned headers before an admin catalog request is routed downstream. */
@Component
public final class AdminCatalogRequestBoundaryFilter implements GlobalFilter, Ordered {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String ACTOR_ID_HEADER = "X-Actor-Id";
    private static final String ADMIN_CATALOG_PATH = "/api/v1/admin/catalog";

    private final GatewayHttpErrorWriter errorWriter;
    private final GatewayTraceIdResolver traceIdResolver;

    public AdminCatalogRequestBoundaryFilter(
            GatewayHttpErrorWriter errorWriter,
            GatewayTraceIdResolver traceIdResolver) {
        this.errorWriter = errorWriter;
        this.traceIdResolver = traceIdResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!isAdminCatalogRequest(exchange)) {
            return chain.filter(exchange);
        }

        String traceId = traceIdResolver.normalizedCallerTraceId(
                exchange.getRequest().getHeaders());
        if (traceId == null) {
            return errorWriter.write(
                    exchange,
                    GatewayErrorCode.INVALID_ADMIN_REQUEST);
        }

        var sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(TRACE_ID_HEADER, traceId);
                    headers.remove(ACTOR_ID_HEADER);
                })
                .build();
        return chain.filter(exchange.mutate().request(sanitizedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean isAdminCatalogRequest(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return path.equals(ADMIN_CATALOG_PATH) || path.startsWith(ADMIN_CATALOG_PATH + "/");
    }
}
