package com.philia.flashsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/** Contract checks for the authenticated public Cart route and deny-by-default documentation. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "API_DOCS_ENABLED=false")
@AutoConfigureWebTestClient
@Import(CartGatewayRouteConfigurationTests.JwtDecoderTestConfiguration.class)
class CartGatewayRouteConfigurationTests {

    @Autowired
    private RouteLocator routes;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void cartServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("CART_SERVICE_URL", () -> "http://cart-service:8080");
    }

    @Test
    void cartApiRouteTargetsCartService() {
        var configured = routes.getRoutes().collectList().block(Duration.ofSeconds(5));
        assertThat(configured).isNotNull();
        Route cartRoute = configured.stream().filter(route -> route.getId().equals("cart-api"))
                .findFirst().orElseThrow();
        assertThat(cartRoute.getUri().toString()).isEqualTo("http://cart-service:8080");
        assertThat(configured).extracting(Route::getId)
                .contains("cart-api", "openapi-cart-service");
    }

    @Test
    void productInternalDisplayRouteIsNotExposedByGateway() {
        var routeIds = routes.getRoutes().map(Route::getId)
                .collectList().block(Duration.ofSeconds(5));
        assertThat(routeIds).isNotNull()
                .noneMatch(id -> id.contains("product-display") || id.contains("cart-product"));
    }

    @Test
    void cartOperationsRequireAuthentication() {
        webTestClient.get().uri("/api/v1/cart")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().jsonPath("$.errorCode").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void documentationRemainsDisabledByDefault() {
        webTestClient.get().uri("/swagger-ui.html")
                .exchange()
                .expectStatus().isUnauthorized();
    }

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
