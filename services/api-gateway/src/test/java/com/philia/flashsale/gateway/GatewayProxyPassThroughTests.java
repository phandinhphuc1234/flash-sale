package com.philia.flashsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(GatewayProxyPassThroughTests.JwtDecoderTestConfiguration.class)
class GatewayProxyPassThroughTests {

    private static final MediaType DOWNSTREAM_CONTENT_TYPE =
            MediaType.parseMediaType("application/problem+json");

    private static final Map<Integer, byte[]> DOWNSTREAM_BODIES = Map.of(
            400, bytes("{\"serviceCode\":\"VALIDATION_FAILED\",\"detail\":\"sản phẩm không hợp lệ\"}\n"),
            404, bytes("{ \"serviceCode\" : \"PRODUCT_NOT_FOUND\", \"productId\" : 404 }"),
            409, bytes("{\"serviceCode\":\"SKU_CONFLICT\",\"details\":[\"sku-1\",\"sku-2\"]}"),
            429, bytes("{\"serviceCode\":\"PRODUCT_RATE_LIMITED\",\"retryable\":true}\n"),
            500, bytes("{\"serviceCode\":\"PRODUCT_INTERNAL_ERROR\",\"opaque\":true}\n"));

    private static final HttpServer DOWNSTREAM = startDownstream();
    private static final AtomicReference<CapturedRequest> CAPTURED_REQUEST = new AtomicReference<>();

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void routeToTestDownstream(DynamicPropertyRegistry registry) {
        registry.add("PRODUCT_SERVICE_URL", () -> "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @ParameterizedTest(name = "downstream HTTP {0} remains service-owned")
    @ValueSource(ints = {400, 404, 409, 429, 500})
    void forwardsDownstreamErrorStatusAndBodyByteForByte(int status) {
        byte[] expectedBody = DOWNSTREAM_BODIES.get(status);

        byte[] actualBody = webTestClient.get()
                .uri("/api/v1/catalog/pass-through/{status}", status)
                .exchange()
                .expectStatus().isEqualTo(status)
                .expectHeader().contentType(DOWNSTREAM_CONTENT_TYPE)
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(actualBody).containsExactly(expectedBody);
    }

    @Test
    void downstreamOwned429KeepsBodyAndHeadersWithoutGatewayQuotaInjection() {
        byte[] expectedBody = DOWNSTREAM_BODIES.get(429);

        WebTestClient.ResponseSpec response = webTestClient.get()
                .uri("/api/v1/catalog/pass-through/429")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().contentType(DOWNSTREAM_CONTENT_TYPE)
                .expectHeader().valueEquals("X-Product-Rate-Limit-Sentinel", "product-owned-429")
                .expectHeader().doesNotExist("Retry-After")
                .expectHeader().value("Cache-Control", value -> assertThat(value).isNotEqualTo("no-store"))
                .expectHeader().doesNotExist("RateLimit")
                .expectHeader().doesNotExist("RateLimit-Policy")
                .expectHeader().doesNotExist("RateLimit-Limit")
                .expectHeader().doesNotExist("RateLimit-Remaining")
                .expectHeader().doesNotExist("RateLimit-Reset");

        byte[] actualBody = response.expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(actualBody).containsExactly(expectedBody);
    }

    @Test
    void forwardsAdminMethodPathQueryBodyAndBoundaryHeaders() {
        CAPTURED_REQUEST.set(null);

        byte[] requestBody = bytes("{\"sku\":\"SKU-001\",\"name\":\"Flash phone\"}");

        webTestClient
                .post()
                .uri(URI.create("/api/v1/admin/catalog/products?source=admin&tag=flash%2Bsale"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer forwarded-test-token")
                .header("X-Trace-Id", "trace-forward-001")
                .header("Idempotency-Key", "create-product-001")
                .header("X-Actor-Id", "caller-controlled-actor")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody().json("{\"downstream\":\"ok\"}");

        CapturedRequest captured = CAPTURED_REQUEST.get();
        assertThat(captured).isNotNull();
        assertThat(captured.method()).isEqualTo("POST");
        assertThat(captured.path()).isEqualTo("/api/v1/admin/catalog/products");
        assertThat(captured.rawQuery()).isEqualTo("source=admin&tag=flash%2Bsale");
        assertThat(captured.body()).isEqualTo(new String(requestBody, StandardCharsets.UTF_8));
        assertThat(captured.authorization()).isEqualTo("Bearer forwarded-test-token");
        assertThat(captured.traceId()).isEqualTo("trace-forward-001");
        assertThat(captured.idempotencyKey()).isEqualTo("create-product-001");
        assertThat(captured.actorId()).isNull();
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                    0);
            server.createContext("/api/v1/catalog/pass-through", GatewayProxyPassThroughTests::respond);
            server.createContext("/api/v1/admin/catalog/products", GatewayProxyPassThroughTests::captureAdminRequest);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void captureAdminRequest(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        var headers = exchange.getRequestHeaders();
        CAPTURED_REQUEST.set(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(),
                new String(body, StandardCharsets.UTF_8),
                headers.getFirst(HttpHeaders.AUTHORIZATION),
                headers.getFirst("X-Trace-Id"),
                headers.getFirst("Idempotency-Key"),
                headers.getFirst("X-Actor-Id")));

        byte[] response = bytes("{\"downstream\":\"ok\"}");
        exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(201, response.length);
        try (exchange; var responseBody = exchange.getResponseBody()) {
            responseBody.write(response);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        int status = statusFrom(exchange);
        byte[] body = DOWNSTREAM_BODIES.get(status);
        if (body == null) {
            body = bytes("{\"serviceCode\":\"TEST_CASE_NOT_FOUND\"}");
            status = 500;
        }

        exchange.getResponseHeaders().set("Content-Type", DOWNSTREAM_CONTENT_TYPE.toString());
        if (status == 429) {
            exchange.getResponseHeaders().set("X-Product-Rate-Limit-Sentinel", "product-owned-429");
        }
        exchange.sendResponseHeaders(status, body.length);
        try (exchange; var responseBody = exchange.getResponseBody()) {
            responseBody.write(body);
        }
    }

    private static int statusFrom(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        return Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private record CapturedRequest(
            String method,
            String path,
            String rawQuery,
            String body,
            String authorization,
            String traceId,
            String idempotencyKey,
            String actorId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtDecoderTestConfiguration {

        @Bean
        ReactiveJwtDecoder testReactiveJwtDecoder() {
            return token -> {
                if (!"forwarded-test-token".equals(token)) {
                    return Mono.error(new IllegalArgumentException("unexpected test token"));
                }
                return Mono.just(Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject("catalog-admin-test")
                        .claim("authorities", List.of("CATALOG_ADMIN"))
                        .issuedAt(Instant.now().minusSeconds(60))
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build());
            };
        }
    }
}
