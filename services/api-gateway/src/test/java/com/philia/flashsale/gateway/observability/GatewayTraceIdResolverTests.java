package com.philia.flashsale.gateway.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class GatewayTraceIdResolverTests {

    private final GatewayTraceIdResolver resolver = new GatewayTraceIdResolver();

    @Test
    void normalizesTheFirstValidCallerTrace() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Trace-Id", "  caller-trace  ");
        headers.add("X-Trace-Id", "ignored-second-value");

        assertThat(resolver.normalizedCallerTraceId(headers)).isEqualTo("caller-trace");
    }

    @Test
    void acceptsTheMaximumLengthCallerTrace() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", "x".repeat(128));

        assertThat(resolver.normalizedCallerTraceId(headers)).isEqualTo("x".repeat(128));
    }

    @Test
    void rejectsBlankAndOverlongCallerTraces() {
        HttpHeaders blankHeaders = new HttpHeaders();
        blankHeaders.set("X-Trace-Id", "   ");
        HttpHeaders overlongHeaders = new HttpHeaders();
        overlongHeaders.set("X-Trace-Id", "x".repeat(129));

        assertThat(resolver.normalizedCallerTraceId(new HttpHeaders())).isNull();
        assertThat(resolver.normalizedCallerTraceId(blankHeaders)).isNull();
        assertThat(resolver.normalizedCallerTraceId(overlongHeaders)).isNull();
    }

    @Test
    void doesNotRescueAnInvalidFirstValueWithASecondValue() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Trace-Id", "   ");
        headers.add("X-Trace-Id", "valid-but-not-canonical");

        assertThat(resolver.normalizedCallerTraceId(headers)).isNull();
    }

    @Test
    void preservesAValidCallerTraceForTheExchange() {
        MockServerWebExchange exchange = exchangeWithTrace("  caller-trace  ");

        assertThat(resolver.resolve(exchange)).isEqualTo("caller-trace");
        assertThat(resolver.resolve(exchange)).isEqualTo("caller-trace");
    }

    @Test
    void generatesOneStableUuidForAnExchangeWithoutAUsableCallerTrace() {
        MockServerWebExchange exchange = exchangeWithTrace("   ");

        String firstResolution = resolver.resolve(exchange);
        String secondResolution = resolver.resolve(exchange);

        assertThat(firstResolution).isNotBlank().isEqualTo(secondResolution);
        assertThat(UUID.fromString(firstResolution).toString()).isEqualTo(firstResolution);
    }

    @Test
    void doesNotShareGeneratedTraceStateAcrossExchanges() {
        String first = resolver.resolve(exchangeWithoutTrace());
        String second = resolver.resolve(exchangeWithoutTrace());

        assertThat(first).isNotEqualTo(second);
    }

    private MockServerWebExchange exchangeWithTrace(String traceId) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/products")
                        .header("X-Trace-Id", traceId)
                        .build());
    }

    private MockServerWebExchange exchangeWithoutTrace() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/products").build());
    }
}
