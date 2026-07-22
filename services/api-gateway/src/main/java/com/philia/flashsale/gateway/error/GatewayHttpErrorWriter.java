package com.philia.flashsale.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Serializes failures produced by the gateway edge. */
@Component
public final class GatewayHttpErrorWriter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final int TRACE_ID_MAX_LENGTH = 128;

    private final ObjectMapper objectMapper;

    public GatewayHttpErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(
            ServerWebExchange exchange,
            GatewayErrorCode errorCode) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(new GatewayErrorResponse(
                    errorCode.name(),
                    errorCode.message(),
                    normalizedTraceId(exchange.getRequest().getHeaders())));
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }

        exchange.getResponse().setStatusCode(errorCode.status());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(body)));
    }

    /** Returns the bounded trace value forwarded to downstream services and error responses. */
    public String normalizedTraceId(HttpHeaders headers) {
        String value = headers.getFirst(TRACE_ID_HEADER);
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() || normalized.length() > TRACE_ID_MAX_LENGTH
                ? null
                : normalized;
    }
}
