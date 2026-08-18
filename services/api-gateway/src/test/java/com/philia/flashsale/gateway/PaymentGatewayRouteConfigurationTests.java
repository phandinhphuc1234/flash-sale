package com.philia.flashsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/** Gateway contract for the authenticated Payment API and exact public webhook exception. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(PaymentGatewayRouteConfigurationTests.JwtDecoderTestConfiguration.class)
class PaymentGatewayRouteConfigurationTests {
    private static final AtomicReference<CapturedRequest> CAPTURED = new AtomicReference<>();
    private static final HttpServer DOWNSTREAM = startDownstream();

    @Autowired
    private RouteLocator routes;
    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void paymentUrl(DynamicPropertyRegistry registry) {
        registry.add("PAYMENT_SERVICE_URL", () ->
                "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @Test
    void paymentApiAndExactWebhookRoutesTargetPaymentService() {
        var configured = routes.getRoutes().collectList().block(Duration.ofSeconds(5));
        assertThat(configured).isNotNull();
        Route paymentApi = configured.stream().filter(route -> route.getId().equals("payment-api"))
                .findFirst().orElseThrow();
        Route webhook = configured.stream().filter(route -> route.getId().equals("payment-webhook"))
                .findFirst().orElseThrow();

        String expectedUri = "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort();
        assertThat(paymentApi.getUri().toString()).isEqualTo(expectedUri);
        assertThat(webhook.getUri().toString()).isEqualTo(expectedUri);
        assertThat(configured).extracting(Route::getId).contains("payment-api", "payment-webhook");
    }

    @Test
    void paymentApiRequiresAuthentication() {
        webTestClient.get().uri("/api/v1/payments/11111111-1111-1111-1111-111111111111")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void authenticatedPaymentQueryForwardsPathAndTrace() {
        CAPTURED.set(null);
        webTestClient.get().uri("/api/v1/payments/11111111-1111-1111-1111-111111111111?view=owner")
                .header(HttpHeaders.AUTHORIZATION, "Bearer payment-gateway-token")
                .header("X-Trace-Id", "payment-gateway-trace")
                .exchange().expectStatus().isOk()
                .expectBody().json("{\"downstream\":\"payment\"}");

        CapturedRequest request = CAPTURED.get();
        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/api/v1/payments/11111111-1111-1111-1111-111111111111");
        assertThat(request.query()).isEqualTo("view=owner");
        assertThat(request.authorization()).isEqualTo("Bearer payment-gateway-token");
        assertThat(request.traceId()).isEqualTo("payment-gateway-trace");
    }

    @Test
    void unsupportedPaymentMethodDoesNotReachPaymentService() {
        webTestClient.delete().uri("/api/v1/payments/11111111-1111-1111-1111-111111111111")
                .header(HttpHeaders.AUTHORIZATION, "Bearer payment-gateway-token")
                .exchange().expectStatus().isNotFound();
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/api/v1/payments/", PaymentGatewayRouteConfigurationTests::respond);
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
                exchange.getRequestURI().getRawQuery(), headers.getFirst(HttpHeaders.AUTHORIZATION),
                headers.getFirst("X-Trace-Id"), body));
        byte[] response = "{\"downstream\":\"payment\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (exchange; var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private record CapturedRequest(String method, String path, String query, String authorization,
            String traceId, byte[] body) { }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class JwtDecoderTestConfiguration {
        @Bean
        @org.springframework.context.annotation.Primary
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return token -> Mono.just(Jwt.withTokenValue(token)
                    .header("alg", "none").header("typ", "at+jwt")
                    .subject("22222222-2222-2222-2222-222222222222")
                    .audience(List.of("flash-sale-api"))
                    .issuedAt(Instant.now().minusSeconds(60))
                    .expiresAt(Instant.now().plusSeconds(300)).build());
        }
    }
}
