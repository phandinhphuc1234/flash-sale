package com.philia.flashsale.gateway;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayUnknownPathSecurityTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void anonymousUnknownPathUsesUnauthenticatedContract() {
        webTestClient.get()
                .uri("/not-a-configured-route")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.message").isEqualTo("Authentication is required")
                .jsonPath("$.traceId").isNotEmpty();
    }

    @Test
    void authenticatedUnknownPathUsesGenericAccessDeniedContract() {
        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/not-a-configured-route")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED")
                .jsonPath("$.message").isEqualTo("Access is denied")
                .jsonPath("$.traceId").isNotEmpty();
    }

    @Test
    void anonymousUnknownActuatorPathStillUsesDenyByDefaultContract() {
        webTestClient.get()
                .uri("/actuator/not-a-real-endpoint")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.traceId").isNotEmpty();
    }

    @Test
    void anonymousUnknownHealthComponentStillUsesDenyByDefaultContract() {
        webTestClient.get()
                .uri("/actuator/health/not-a-real-component")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.traceId").isNotEmpty();
    }

    @Test
    void authenticatedUnknownActuatorPathUsesGenericAccessDeniedContract() {
        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/actuator/not-a-real-endpoint")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED")
                .jsonPath("$.traceId").isNotEmpty();
    }

    @Test
    void configuredHealthEndpointRemainsPublic() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
