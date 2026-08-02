package com.philia.flashsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(GatewayAuthenticationFailureContractTests.JwtDecoderTestConfiguration.class)
class GatewayAuthenticationFailureContractTests {

    private static final String PROVIDER_SENTINEL = "jwks.internal.example:8443";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void genericInvalidJwtRemainsUnauthenticated() {
        webTestClient.get()
                .uri("/api/v1/admin/catalog/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectHeader().valueMatches("X-Trace-Id", ".+")
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.message").isEqualTo("Authentication is required")
                .jsonPath("$.traceId").doesNotExist();
    }

    @Test
    void nestedVerificationInfrastructureFailureUsesSafeServiceUnavailableContract() {
        webTestClient.get()
                .uri("/api/v1/admin/catalog/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer verification-unavailable")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
                .expectHeader().valueMatches("X-Trace-Id", ".+")
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("AUTHENTICATION_UNAVAILABLE")
                .jsonPath("$.message").isEqualTo("Authentication is temporarily unavailable")
                .jsonPath("$.traceId").doesNotExist()
                .consumeWith(result -> assertThat(new String(
                                result.getResponseBody(), StandardCharsets.UTF_8))
                        .doesNotContain(PROVIDER_SENTINEL)
                        .doesNotContain("BadJwtException")
                        .doesNotContain("ConnectException"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtDecoderTestConfiguration {

        @Bean
        @Primary
        ReactiveJwtDecoder testReactiveJwtDecoder() {
            return token -> {
                if ("verification-unavailable".equals(token)) {
                    return Mono.error(new BadJwtException(
                            "verification failed at " + PROVIDER_SENTINEL,
                            new ConnectException(PROVIDER_SENTINEL)));
                }
                return Mono.error(new BadJwtException("invalid JWT"));
            };
        }
    }
}
