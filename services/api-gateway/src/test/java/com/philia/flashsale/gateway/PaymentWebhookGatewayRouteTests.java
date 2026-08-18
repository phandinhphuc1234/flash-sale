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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/** Exact webhook route is unauthenticated only for POST and forwards signed bytes unchanged. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(PaymentWebhookGatewayRouteTests.JwtDecoderTestConfiguration.class)
class PaymentWebhookGatewayRouteTests {
    private static final AtomicReference<CapturedRequest> CAPTURED = new AtomicReference<>();
    private static final HttpServer DOWNSTREAM = startDownstream();

    @Autowired RouteLocator routes;
    @Autowired WebTestClient client;

    @DynamicPropertySource
    static void paymentUrl(DynamicPropertyRegistry registry) {
        registry.add("PAYMENT_SERVICE_URL", () -> "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @Test
    void webhookRouteIsConfiguredAndPostIsAllowedWithoutJwt() {
        assertThat(routes.getRoutes().map(Route::getId).collectList().block(Duration.ofSeconds(5)))
                .contains("payment-webhook", "payment-api");

        byte[] body = "{\"event\":\"signed\"}".getBytes(StandardCharsets.UTF_8);
        CAPTURED.set(null);
        client.post().uri("/webhooks/v1/payments/stripe")
                .header("Stripe-Signature", "t=1,v1=signature")
                .header("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange()
                .expectStatus().isNoContent();

        CapturedRequest request = CAPTURED.get();
        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.body()).isEqualTo(body);
        assertThat(request.signature()).isEqualTo("t=1,v1=signature");
        assertThat(request.traceparent()).isEqualTo(
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
    }

    @Test
    void webhookGetAndUnknownPathsRemainDenied() {
        client.get().uri("/webhooks/v1/payments/stripe").exchange().expectStatus().isUnauthorized();
        client.post().uri("/webhooks/v1/payments/stripe/extra").exchange().expectStatus().isUnauthorized();
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/webhooks/v1/payments/stripe", PaymentWebhookGatewayRouteTests::respond);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        var headers = exchange.getRequestHeaders();
        CAPTURED.set(new CapturedRequest(exchange.getRequestMethod(), body,
                headers.getFirst("Stripe-Signature"), headers.getFirst("traceparent")));
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private record CapturedRequest(String method, byte[] body, String signature, String traceparent) {
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class JwtDecoderTestConfiguration {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return token -> Mono.just(Jwt.withTokenValue(token)
                    .header("alg", "none").header("typ", "at+jwt")
                    .subject("00000000-0000-0000-0000-000000000003")
                    .audience(List.of("flash-sale-api"))
                    .issuedAt(java.time.Instant.now().minusSeconds(60))
                    .expiresAt(java.time.Instant.now().plusSeconds(300)).build());
        }
    }
}
