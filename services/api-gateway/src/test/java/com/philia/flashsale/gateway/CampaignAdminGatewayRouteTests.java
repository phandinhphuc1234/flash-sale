package com.philia.flashsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/** Contract tests for the Campaign admin route before its production wiring is added in T032. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(CampaignAdminGatewayRouteTests.JwtDecoderTestConfiguration.class)
class CampaignAdminGatewayRouteTests {

    private static final String CAMPAIGN_ADMIN_TOKEN = "campaign-admin-token";
    private static final String CAMPAIGN_VIEWER_TOKEN = "campaign-viewer-token";
    private static final String WRONG_AUDIENCE_TOKEN = "wrong-audience-token";
    private static final AtomicReference<CapturedRequest> CAPTURED_REQUEST = new AtomicReference<>();
    private static final HttpServer DOWNSTREAM = startDownstream();

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void routeToTestDownstream(DynamicPropertyRegistry registry) {
        registry.add("CAMPAIGN_SERVICE_URL", () ->
                "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @Test
    void campaignAdminRouteIsConfigured() {
        assertThat(routeLocator.getRoutes()
                .map(Route::getId)
                .collectList()
                .block())
                .contains("campaign-admin");
    }

    @Test
    void campaignAdminRequiresAuthentication() {
        webTestClient.get()
                .uri("/api/v1/admin/campaigns/campaign-001")
                .header("X-Trace-Id", "campaign-unauthenticated")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.traceId").doesNotExist();
    }

    @Test
    void campaignAdminRejectsTokenForWrongPublicAudience() {
        webTestClient.get()
                .uri("/api/v1/admin/campaigns/campaign-001")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + WRONG_AUDIENCE_TOKEN)
                .header("X-Trace-Id", "campaign-wrong-audience")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.traceId").doesNotExist();
    }

    @Test
    void campaignAdminRequiresCampaignAdminScope() {
        webTestClient.get()
                .uri("/api/v1/admin/campaigns/campaign-001")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + CAMPAIGN_VIEWER_TOKEN)
                .header("X-Trace-Id", "campaign-missing-scope")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.traceId").doesNotExist();
    }

    @Test
    void forwardsCampaignMethodPathQueryBodyAndRequiredHeaders() {
        CAPTURED_REQUEST.set(null);
        byte[] requestBody = bytes("{\"name\":\"Flash phone\",\"code\":\"FLASH-001\"}");

        webTestClient.post()
                .uri("/api/v1/admin/campaigns?source=admin&tag=flash%2Bsale")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + CAMPAIGN_ADMIN_TOKEN)
                .header("X-Trace-Id", "campaign-forward-001")
                .header("Idempotency-Key", "campaign-create-001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.downstream").isEqualTo("ok");

        CapturedRequest captured = CAPTURED_REQUEST.get();
        assertThat(captured).isNotNull();
        assertThat(captured.method()).isEqualTo("POST");
        assertThat(captured.path()).isEqualTo("/api/v1/admin/campaigns");
        assertThat(captured.rawQuery()).isEqualTo("source=admin&tag=flash%2Bsale");
        assertThat(captured.body()).isEqualTo(new String(requestBody, StandardCharsets.UTF_8));
        assertThat(captured.authorization()).isEqualTo("Bearer " + CAMPAIGN_ADMIN_TOKEN);
        assertThat(captured.traceId()).isEqualTo("campaign-forward-001");
        assertThat(captured.idempotencyKey()).isEqualTo("campaign-create-001");
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/api/v1/admin/campaigns", CampaignAdminGatewayRouteTests::respond);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        var headers = exchange.getRequestHeaders();
        CAPTURED_REQUEST.set(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(),
                new String(body, StandardCharsets.UTF_8),
                headers.getFirst(HttpHeaders.AUTHORIZATION),
                headers.getFirst("X-Trace-Id"),
                headers.getFirst("Idempotency-Key")));

        byte[] response = bytes("{\"downstream\":\"ok\"}");
        exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(200, response.length);
        try (exchange; var responseBody = exchange.getResponseBody()) {
            responseBody.write(response);
        }
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
            String idempotencyKey) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtDecoderTestConfiguration {

        @Bean
        @Primary
        ReactiveJwtDecoder testReactiveJwtDecoder() {
            return token -> switch (token) {
                case WRONG_AUDIENCE_TOKEN -> Mono.error(new BadJwtException("wrong audience"));
                case CAMPAIGN_ADMIN_TOKEN -> Mono.just(jwt(token, "CAMPAIGN_ADMIN"));
                case CAMPAIGN_VIEWER_TOKEN -> Mono.just(jwt(token, "CAMPAIGN_VIEWER"));
                default -> Mono.error(new BadJwtException("invalid JWT"));
            };
        }

        private static Jwt jwt(String token, String scope) {
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .header("typ", "at+jwt")
                    .subject("campaign-admin-test")
                    .audience(List.of("flash-sale-api"))
                    .claim("scope", scope)
                    .issuedAt(Instant.now().minusSeconds(60))
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        }
    }
}
