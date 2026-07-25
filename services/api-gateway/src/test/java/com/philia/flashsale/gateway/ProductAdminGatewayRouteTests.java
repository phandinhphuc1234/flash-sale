package com.philia.flashsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.philia.flashsale.gateway.filter.global.AdminCatalogRequestBoundaryFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ProductAdminGatewayRouteTests {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AdminCatalogRequestBoundaryFilter adminCatalogRequestBoundaryFilter;

    @Test
    void productAdminRouteIsConfigured() {
        assertThat(routeLocator.getRoutes()
                        .map(Route::getId)
                        .collectList()
                        .block(Duration.ofSeconds(5)))
                .contains("product-catalog-admin");
    }

    @Test
    void adminCatalogRequiresAuthentication() {
        webTestClient.get()
                .uri("/api/v1/admin/catalog/products")
                .header("X-Trace-Id", "trace-unauthenticated")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.message").isEqualTo("Authentication is required")
                .jsonPath("$.traceId").isEqualTo("trace-unauthenticated");
    }

    @Test
    void adminCatalogRequiresCatalogAdminAuthority() {
        webTestClient
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("CATALOG_VIEWER")))
                .get()
                .uri("/api/v1/admin/catalog/products")
                .header("X-Trace-Id", "trace-forbidden")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
                .expectBody()
                .jsonPath("$.code").isEqualTo("CATALOG_ADMIN_REQUIRED")
                .jsonPath("$.message").isEqualTo("CATALOG_ADMIN authority is required")
                .jsonPath("$.traceId").isEqualTo("trace-forbidden");
    }

    @Test
    void authenticatedAdminRequestRequiresTraceId() {
        webTestClient
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("CATALOG_ADMIN")))
                .get()
                .uri("/api/v1/admin/catalog/products")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_ADMIN_REQUEST")
                .jsonPath("$.message")
                .isEqualTo("X-Trace-Id must be non-blank and no longer than 128 characters")
                .jsonPath("$.traceId").isNotEmpty();
    }

    @Test
    void adminBoundaryNormalizesTraceAndRemovesCallerActorHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/admin/catalog/products?source=test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .header("Idempotency-Key", "create-product-001")
                        .header("X-Trace-Id", "  trace-normalized  ")
                        .header("X-Actor-Id", "caller-controlled-actor")
                        .build());
        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();

        adminCatalogRequestBoundaryFilter.filter(exchange, filteredExchange -> {
            forwardedExchange.set(filteredExchange);
            return Mono.empty();
        }).block(Duration.ofSeconds(5));

        assertThat(forwardedExchange.get()).isNotNull();
        HttpHeaders forwardedHeaders = forwardedExchange.get().getRequest().getHeaders();
        assertThat(forwardedHeaders.getFirst("X-Trace-Id")).isEqualTo("trace-normalized");
        assertThat(forwardedHeaders.containsKey("X-Actor-Id")).isFalse();
        assertThat(forwardedHeaders.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-token");
        assertThat(forwardedHeaders.getFirst("Idempotency-Key")).isEqualTo("create-product-001");
        assertThat(forwardedExchange.get().getRequest().getURI().getRawQuery()).isEqualTo("source=test");
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Actor-Id"))
                .isEqualTo("caller-controlled-actor");
    }

    @Test
    void overlongTraceIdIsRejectedBeforeGatewayChain() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/catalog/products")
                        .header("X-Trace-Id", "x".repeat(129))
                        .build());
        AtomicBoolean chainInvoked = new AtomicBoolean();

        adminCatalogRequestBoundaryFilter.filter(exchange, filteredExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        }).block(Duration.ofSeconds(5));

        assertThat(chainInvoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(5)))
                .contains("\"code\":\"INVALID_ADMIN_REQUEST\"")
                .doesNotContain("\"traceId\":null")
                .containsPattern("\\\"traceId\\\":\\\"[^\\\"]+\\\"");
    }

    @Test
    void blankTraceIdIsRejectedBeforeGatewayChain() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/catalog/products")
                        .header("X-Trace-Id", "   ")
                        .build());
        AtomicBoolean chainInvoked = new AtomicBoolean();

        adminCatalogRequestBoundaryFilter.filter(exchange, filteredExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        }).block(Duration.ofSeconds(5));

        assertThat(chainInvoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(5)))
                .contains("\"code\":\"INVALID_ADMIN_REQUEST\"")
                .doesNotContain("\"traceId\":null")
                .containsPattern("\\\"traceId\\\":\\\"[^\\\"]+\\\"");
    }

    @Test
    void maximumLengthTraceIdIsForwarded() {
        String traceId = "x".repeat(128);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/catalog/products")
                        .header("X-Trace-Id", traceId)
                        .build());
        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();

        adminCatalogRequestBoundaryFilter.filter(exchange, filteredExchange -> {
            forwardedExchange.set(filteredExchange);
            return Mono.empty();
        }).block(Duration.ofSeconds(5));

        assertThat(forwardedExchange.get()).isNotNull();
        assertThat(forwardedExchange.get().getRequest().getHeaders().getFirst("X-Trace-Id"))
                .isEqualTo(traceId);
    }
}
