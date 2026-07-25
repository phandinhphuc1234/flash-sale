package com.philia.flashsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(GatewayUnexpectedFailureTests.FailureInjectionConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GatewayUnexpectedFailureTests {

    private static final String FAILURE_PATH = "/api/v1/catalog/failure-injection";
    private static final String BEARER_SENTINEL = "Bearer client-token-must-not-leak";
    private static final String BODY_SENTINEL = "request-body-must-not-leak";
    private static final String HOST_SENTINEL = "product-service.internal:9080";
    private static final String RAW_MESSAGE =
            "synthetic failure " + BEARER_SENTINEL + " " + BODY_SENTINEL + " " + HOST_SENTINEL;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsSafeGeneratedTraceEnvelopeForUnexpectedGatewayFailure() throws Exception {
        JsonNode body = unexpectedFailureResponse(null);

        assertExactInternalErrorEnvelope(body);
        assertThat(body.path("traceId").asText()).isNotBlank();
        assertNoSensitiveDetail(body.toString());
    }

    @Test
    void preservesValidTraceWithoutExposingUnexpectedFailureDetails() throws Exception {
        JsonNode body = unexpectedFailureResponse(" trace-unexpected-failure ");

        assertExactInternalErrorEnvelope(body);
        assertThat(body.path("traceId").asText()).isEqualTo("trace-unexpected-failure");
        assertNoSensitiveDetail(body.toString());
    }

    private JsonNode unexpectedFailureResponse(String traceId) throws Exception {
        WebTestClient.RequestBodySpec request = webTestClient.post()
                .uri(FAILURE_PATH)
                .contentType(MediaType.APPLICATION_JSON);
        if (traceId != null) {
            request.header("X-Trace-Id", traceId);
        }

        byte[] responseBody = request
                .bodyValue("{\"value\":\"" + BODY_SENTINEL + "\"}")
                .exchange()
                .expectStatus().isEqualTo(500)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(responseBody).isNotNull();
        return objectMapper.readTree(responseBody);
    }

    private void assertExactInternalErrorEnvelope(JsonNode body) {
        assertThat(body.size()).isEqualTo(3);
        assertThat(body.path("code").asText()).isEqualTo("GATEWAY_INTERNAL_ERROR");
        assertThat(body.path("message").asText()).isEqualTo("The gateway could not process the request");
        assertThat(body.hasNonNull("traceId")).isTrue();
    }

    private void assertNoSensitiveDetail(String responseBody) {
        assertThat(responseBody)
                .doesNotContain(BEARER_SENTINEL)
                .doesNotContain(BODY_SENTINEL)
                .doesNotContain(HOST_SENTINEL)
                .doesNotContain(RAW_MESSAGE)
                .doesNotContain("IllegalStateException");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureInjectionConfiguration {

        @Bean
        GlobalFilter unexpectedFailureInjectionFilter() {
            return new UnexpectedFailureInjectionFilter();
        }
    }

    private static final class UnexpectedFailureInjectionFilter implements GlobalFilter, Ordered {

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            if (FAILURE_PATH.equals(exchange.getRequest().getPath().value())) {
                return Mono.error(new IllegalStateException(RAW_MESSAGE));
            }
            return chain.filter(exchange);
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE + 100;
        }
    }
}
