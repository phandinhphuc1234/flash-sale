package com.philia.flashsale.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.gateway.observability.GatewayErrorObservation;
import com.philia.flashsale.gateway.observability.GatewayTraceIdResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class GatewayHttpErrorWriterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GatewayTraceIdResolver traceIdResolver = new GatewayTraceIdResolver();
    private final GatewayErrorObservation observation = mock(GatewayErrorObservation.class);
    private final GatewayHttpErrorWriter writer =
            new GatewayHttpErrorWriter(objectMapper, traceIdResolver, observation);

    @Test
    void writesTheExactGatewayEnvelopeAndOnlySafeObservationFields() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/admin/catalog/products")
                        .header("X-Trace-Id", "  trace-503  ")
                        .build());
        IllegalStateException failure =
                new IllegalStateException("secret-token internal-host:9443 must not be observed");

        writer.write(exchange, GatewayErrorCode.DOWNSTREAM_UNAVAILABLE, failure)
                .block(Duration.ofSeconds(5));

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(5)))
                .isEqualTo("{\"code\":\"DOWNSTREAM_UNAVAILABLE\","
                        + "\"message\":\"The requested service is temporarily unavailable\","
                        + "\"traceId\":\"trace-503\"}");
        verify(observation).record(
                GatewayErrorCode.DOWNSTREAM_UNAVAILABLE,
                "trace-503",
                "POST",
                "/api/v1/admin/catalog/products",
                IllegalStateException.class.getName());
    }

    @Test
    void generatesANonBlankTraceWhenTheCallerDidNotProvideOne() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/products").build());

        writer.write(exchange, GatewayErrorCode.GATEWAY_INTERNAL_ERROR)
                .block(Duration.ofSeconds(5));

        JsonNode body = objectMapper.readTree(
                exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(5)));
        String traceId = body.path("traceId").asText();
        assertThat(traceId).isNotBlank();
        assertThat(UUID.fromString(traceId).toString()).isEqualTo(traceId);
        verify(observation).record(
                GatewayErrorCode.GATEWAY_INTERNAL_ERROR,
                traceId,
                "GET",
                "/api/v1/catalog/products",
                null);
    }

    @Test
    void writesTheExactRateLimitContractWithTheExistingTraceSemantics() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/products")
                        .header("X-Trace-Id", "  trace-rate-limit  ")
                        .build());

        writer.writeRateLimitExceeded(exchange, Duration.ofMillis(1))
                .block(Duration.ofSeconds(5));

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("1");
        assertThat(exchange.getResponse().getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
        assertNoForbiddenRateLimitAccountingHeaders(exchange);
        assertThat(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(5)))
                .isEqualTo("{\"code\":\"RATE_LIMIT_EXCEEDED\","
                        + "\"message\":\"Too many requests\","
                        + "\"traceId\":\"trace-rate-limit\"}");
        verifyNoInteractions(observation);
    }

    @Test
    void roundsRetryAfterUpToTheNextPositiveSecond() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/products")
                        .header("X-Trace-Id", "trace-rate-limit-rounding")
                        .build());

        writer.writeRateLimitExceeded(exchange, Duration.ofMillis(1_201))
                .block(Duration.ofSeconds(5));

        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("2");
        assertThat(exchange.getResponse().getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
        assertNoForbiddenRateLimitAccountingHeaders(exchange);
    }

    @Test
    void doesNotRewriteOrObserveAnAlreadyCommittedResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/products").build());
        exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
        exchange.getResponse().setComplete().block(Duration.ofSeconds(5));

        IllegalStateException failure = new IllegalStateException("late failure");

        Throwable propagated = catchThrowable(() -> writer.write(
                        exchange,
                        GatewayErrorCode.GATEWAY_INTERNAL_ERROR,
                        failure)
                .block(Duration.ofSeconds(5)));

        assertThat(propagated).isSameAs(failure);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(5))).isEmpty();
        verifyNoInteractions(observation);
    }

    @Test
    void usesASafeInternalEnvelopeWhenNormalSerializationFails() throws Exception {
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
        JsonProcessingException serializationFailure = new JsonProcessingException(
                "secret serialization detail") {
        };
        when(failingObjectMapper.writeValueAsBytes(any())).thenThrow(serializationFailure);
        GatewayHttpErrorWriter fallbackWriter = new GatewayHttpErrorWriter(
                failingObjectMapper,
                traceIdResolver,
                observation);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/products")
                        .header("X-Trace-Id", "trace-fallback")
                        .build());

        fallbackWriter.write(
                        exchange,
                        GatewayErrorCode.DOWNSTREAM_UNAVAILABLE,
                        new IllegalStateException("original failure"))
                .block(Duration.ofSeconds(5));

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(5)))
                .isEqualTo("{\"code\":\"GATEWAY_INTERNAL_ERROR\","
                        + "\"message\":\"The gateway could not process the request\","
                        + "\"traceId\":\"trace-fallback\"}")
                .doesNotContain("secret serialization detail")
                .doesNotContain("original failure");
        verify(observation).record(
                GatewayErrorCode.GATEWAY_INTERNAL_ERROR,
                "trace-fallback",
                "GET",
                "/api/v1/catalog/products",
                serializationFailure.getClass().getName());
    }

    @Test
    void rateLimitSerializationFallbackRemovesQuotaHeadersAndObservesCentralInternalError() throws Exception {
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
        JsonProcessingException serializationFailure = new JsonProcessingException(
                "rate limit serialization detail") {
        };
        when(failingObjectMapper.writeValueAsBytes(any())).thenThrow(serializationFailure);
        GatewayHttpErrorWriter fallbackWriter = new GatewayHttpErrorWriter(
                failingObjectMapper,
                traceIdResolver,
                observation);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/products")
                        .header("X-Trace-Id", "trace-rate-limit-fallback")
                        .build());

        fallbackWriter.writeRateLimitExceeded(exchange, Duration.ofMillis(1))
                .block(Duration.ofSeconds(5));

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getHeaders().containsKey("Retry-After")).isFalse();
        assertThat(exchange.getResponse().getHeaders().containsKey("Cache-Control")).isFalse();
        assertNoForbiddenRateLimitAccountingHeaders(exchange);
        assertThat(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(5)))
                .isEqualTo("{\"code\":\"GATEWAY_INTERNAL_ERROR\","
                        + "\"message\":\"The gateway could not process the request\","
                        + "\"traceId\":\"trace-rate-limit-fallback\"}")
                .doesNotContain("rate limit serialization detail");
        verify(observation).record(
                GatewayErrorCode.GATEWAY_INTERNAL_ERROR,
                "trace-rate-limit-fallback",
                "GET",
                "/api/v1/catalog/products",
                serializationFailure.getClass().getName());
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
