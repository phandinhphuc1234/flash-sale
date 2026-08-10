package com.philia.flashsale.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.common.web.ApiErrorResponse;
import com.philia.flashsale.gateway.observability.GatewayErrorObservation;
import com.philia.flashsale.gateway.observability.GatewayTraceIdResolver;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Serializes failures produced by the gateway edge. */
@Component
public final class GatewayHttpErrorWriter {

    private final ObjectMapper objectMapper;
    private final GatewayTraceIdResolver traceIdResolver;
    private final GatewayErrorObservation errorObservation;

    public GatewayHttpErrorWriter(
            ObjectMapper objectMapper,
            GatewayTraceIdResolver traceIdResolver,
            GatewayErrorObservation errorObservation) {
        // Ensure the shared envelope timestamp is serializable even when
        // this writer is constructed directly in a focused unit test.
        objectMapper.findAndRegisterModules();
        this.objectMapper = objectMapper;
        this.traceIdResolver = traceIdResolver;
        this.errorObservation = errorObservation;
    }

    public Mono<Void> write(
            ServerWebExchange exchange,
            GatewayErrorCode errorCode) {
        return write(exchange, errorCode, null);
    }

    public Mono<Void> writeRateLimitExceeded(
            ServerWebExchange exchange,
            Duration retryAfter) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        String traceId = traceIdResolver.resolve(exchange);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(ApiErrorResponse.of(
                    GatewayErrorCode.RATE_LIMIT_EXCEEDED.name(),
                    GatewayErrorCode.RATE_LIMIT_EXCEEDED.message()));
        } catch (JsonProcessingException exception) {
            removeRateLimitHeaders(exchange.getResponse().getHeaders());
            body = fallbackInternalErrorBody(traceId);
            exchange.getResponse().setStatusCode(GatewayErrorCode.GATEWAY_INTERNAL_ERROR.status());
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
            errorObservation.record(
                    GatewayErrorCode.GATEWAY_INTERNAL_ERROR,
                    traceId,
                    exchange.getRequest().getMethod().name(),
                    exchange.getRequest().getPath().value(),
                    exception.getClass().getName());
            return exchange.getResponse().writeWith(Mono.just(
                    exchange.getResponse().bufferFactory().wrap(body)));
        }

        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Trace-Id", traceId);
        headers.set(HttpHeaders.RETRY_AFTER, retryAfterSeconds(retryAfter));
        headers.setCacheControl("no-store");
        exchange.getResponse().setStatusCode(GatewayErrorCode.RATE_LIMIT_EXCEEDED.status());
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(body)));
    }

    public Mono<Void> write(
            ServerWebExchange exchange,
            GatewayErrorCode errorCode,
            Throwable failure) {
        // A committed downstream response belongs to its original owner and must not be replaced.
        if (exchange.getResponse().isCommitted()) {
            return failure == null ? Mono.empty() : Mono.error(failure);
        }

        String traceId = traceIdResolver.resolve(exchange);
        GatewayErrorCode renderedErrorCode = errorCode;
        Throwable observedFailure = failure;
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(ApiErrorResponse.of(
                    errorCode.name(),
                    errorCode.message()));
        } catch (JsonProcessingException exception) {
            // A minimal encoder keeps the public contract safe even if normal JSON mapping fails.
            renderedErrorCode = GatewayErrorCode.GATEWAY_INTERNAL_ERROR;
            observedFailure = exception;
            body = fallbackInternalErrorBody(traceId);
        }

        exchange.getResponse().setStatusCode(renderedErrorCode.status());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
        errorObservation.record(
                renderedErrorCode,
                traceId,
                exchange.getRequest().getMethod().name(),
                exchange.getRequest().getPath().value(),
                observedFailure == null ? null : observedFailure.getClass().getName());
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(body)));
    }

    private byte[] fallbackInternalErrorBody(String traceId) {
        String body = "{\"success\":false,\"errorCode\":\"GATEWAY_INTERNAL_ERROR\","
                + "\"message\":\"The gateway could not process the request\",\"errors\":null,\"timestamp\":\""
                + Instant.now() + "\"}";
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static String retryAfterSeconds(Duration retryAfter) {
        if (retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()) {
            throw new IllegalArgumentException("retry-after duration must be positive");
        }
        long millis = retryAfter.toMillis();
        long seconds = Math.max(1L, Math.ceilDiv(millis, 1_000L));
        return Long.toString(seconds);
    }

    private static void removeRateLimitHeaders(HttpHeaders headers) {
        headers.remove(HttpHeaders.RETRY_AFTER);
        headers.remove(HttpHeaders.CACHE_CONTROL);
        headers.remove("RateLimit");
        headers.remove("RateLimit-Policy");
        headers.remove("RateLimit-Limit");
        headers.remove("RateLimit-Remaining");
        headers.remove("RateLimit-Reset");
        headers.keySet().removeIf(name -> name.regionMatches(true, 0, "X-RateLimit-", 0, "X-RateLimit-".length()));
    }
}
