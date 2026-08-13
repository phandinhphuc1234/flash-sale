package com.philia.flashsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(FlashSaleGatewayRouteTests.JwtDecoderTestConfiguration.class)
class FlashSaleGatewayRouteTests {
    private static final HttpServer DOWNSTREAM = startDownstream();
    private static final AtomicReference<CapturedRequest> CAPTURED = new AtomicReference<>();

    @Autowired
    private RouteLocator routes;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void downstreamUrl(DynamicPropertyRegistry registry) {
        registry.add("FLASHSALE_SERVICE_URL", () -> "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @Test
    void flashSaleRouteIsConfigured() {
        assertThat(routes.getRoutes().map(Route::getId).collectList().block(Duration.ofSeconds(5)))
                .contains("flash-sale-public");
    }

    @Test
    void flashSaleRouteRequiresAuthentication() {
        webTestClient.post()
                .uri("/api/v1/flash-sales/00000000-0000-0000-0000-000000000001/reservations")
                .header("Idempotency-Key", "gateway-auth-required")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void forwardsPathQueryBodyAndBoundaryHeadersWithoutBusinessInterpretation() {
        byte[] body = "{\"variantId\":\"00000000-0000-0000-0000-000000000002\",\"quantity\":1}"
                .getBytes(StandardCharsets.UTF_8);
        CAPTURED.set(null);

        webTestClient.post()
                .uri("/api/v1/flash-sales/00000000-0000-0000-0000-000000000001/reservations?source=smoke")
                .header(HttpHeaders.AUTHORIZATION, "Bearer forwarded-test-token")
                .header("Idempotency-Key", "gateway-key-001")
                .header("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")
                .header("tracestate", "vendor=value")
                .header("X-Trace-Id", "gateway-trace-001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, "/api/v1/flash-sales/reservations/00000000-0000-0000-0000-000000000004")
                .expectBody().json("{\"downstream\":\"accepted\"}");

        CapturedRequest request = CAPTURED.get();
        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/api/v1/flash-sales/00000000-0000-0000-0000-000000000001/reservations");
        assertThat(request.query()).isEqualTo("source=smoke");
        assertThat(request.body()).isEqualTo(new String(body, StandardCharsets.UTF_8));
        assertThat(request.authorization()).isEqualTo("Bearer forwarded-test-token");
        assertThat(request.idempotencyKey()).isEqualTo("gateway-key-001");
        assertThat(request.traceparent()).isEqualTo("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
        assertThat(request.tracestate()).isEqualTo("vendor=value");
        assertThat(request.traceId()).isEqualTo("gateway-trace-001");
    }

    @Test
    void forwardsAnAuthenticatedOwnerReservationQueryWithoutInterpretingIt() {
        CAPTURED.set(null);

        webTestClient.get()
                .uri("/api/v1/flash-sales/reservations/00000000-0000-0000-0000-000000000004?view=owner")
                .header(HttpHeaders.AUTHORIZATION, "Bearer query-test-token")
                .header("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")
                .header("X-Trace-Id", "gateway-trace-query")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"downstream\":\"reservation\"}");

        CapturedRequest request = CAPTURED.get();
        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/api/v1/flash-sales/reservations/00000000-0000-0000-0000-000000000004");
        assertThat(request.query()).isEqualTo("view=owner");
        assertThat(request.body()).isEmpty();
        assertThat(request.authorization()).isEqualTo("Bearer query-test-token");
        assertThat(request.traceparent()).isEqualTo("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
        assertThat(request.traceId()).isEqualTo("gateway-trace-query");
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/api/v1/flash-sales", FlashSaleGatewayRouteTests::respond);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        var headers = exchange.getRequestHeaders();
        CAPTURED.set(new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(), new String(body, StandardCharsets.UTF_8),
                headers.getFirst(HttpHeaders.AUTHORIZATION), headers.getFirst("Idempotency-Key"), headers.getFirst("traceparent"),
                headers.getFirst("tracestate"), headers.getFirst("X-Trace-Id")));
        boolean ownerQuery = "GET".equals(exchange.getRequestMethod());
        byte[] response = (ownerQuery ? "{\"downstream\":\"reservation\"}" : "{\"downstream\":\"accepted\"}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (!ownerQuery) {
            exchange.getResponseHeaders().set(HttpHeaders.LOCATION,
                    "/api/v1/flash-sales/reservations/00000000-0000-0000-0000-000000000004");
        }
        exchange.sendResponseHeaders(ownerQuery ? 200 : 202, response.length);
        try (exchange; var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private record CapturedRequest(String method, String path, String query, String body,
            String authorization, String idempotencyKey, String traceparent, String tracestate, String traceId) {
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class JwtDecoderTestConfiguration {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return token -> Mono.just(Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .header("typ", "at+jwt")
                    .subject("00000000-0000-0000-0000-000000000003")
                    .audience(List.of("flash-sale-api"))
                    .issuedAt(java.time.Instant.now().minusSeconds(60))
                    .expiresAt(java.time.Instant.now().plusSeconds(300))
                    .build());
        }
    }
}
