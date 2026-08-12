package com.philia.flashsale.gateway.observability;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/** Resolves one safe correlation identifier for the lifetime of a gateway exchange. */
@Component
public final class GatewayTraceIdResolver {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final int TRACE_ID_MAX_LENGTH = 128;
    private static final String TRACE_ID_ATTRIBUTE =
            GatewayTraceIdResolver.class.getName() + ".traceId";

    public String normalizedCallerTraceId(HttpHeaders headers) {
        String value = headers.getFirst(TRACE_ID_HEADER);
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isBlank() || normalized.length() > TRACE_ID_MAX_LENGTH
                ? null
                : normalized;
    }

    public String resolve(ServerWebExchange exchange) {
        String resolved = exchange.getAttribute(TRACE_ID_ATTRIBUTE);
        if (resolved != null) {
            return resolved;
        }

        // Generation supplies error correlation only; admin validation still uses the method above.
        String callerTraceId = normalizedCallerTraceId(exchange.getRequest().getHeaders());
        resolved = callerTraceId == null ? UUID.randomUUID().toString() : callerTraceId;
        exchange.getAttributes().put(TRACE_ID_ATTRIBUTE, resolved);
        return resolved;
    }
}
