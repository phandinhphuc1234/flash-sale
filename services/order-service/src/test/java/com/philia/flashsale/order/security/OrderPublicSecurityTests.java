package com.philia.flashsale.order.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

/** Verifies the Order route has an independent bearer authentication boundary. */
@SpringBootTest(properties = {
        "order.creation.enabled=false",
        "order.query.enabled=false",
        "order.security.jwt.enabled=false",
        "order.runtime.accepted-purchase-consumer-enabled=false",
        "order.runtime.outbox-publisher-enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "liquibase.integration.spring.boot3.autoconfigure.LiquibaseAutoConfiguration"
})
@AutoConfigureMockMvc
@Import(OrderPublicSecurityTests.TestSecurityConfiguration.class)
class OrderPublicSecurityTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void missingBearerTokenUsesTheShared401Envelope() throws Exception {
        mvc.perform(get("/api/v1/orders/00000000-0000-0000-0000-000000000001")
                        .header("X-Trace-Id", "security-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void decoderFailureIsSanitizedAsAuthenticationRequired() throws Exception {
        mvc.perform(get("/api/v1/orders/00000000-0000-0000-0000-000000000001")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityConfiguration {
        @Bean("orderJwtDecoder")
        JwtDecoder orderJwtDecoder() {
            return token -> {
                if ("invalid-token".equals(token)) {
                    throw new BadJwtException("invalid token");
                }
                return Jwt.withTokenValue(token).header("typ", "at+jwt")
                        .subject("00000000-0000-0000-0000-000000000001")
                        .audience(List.of("flash-sale-api"))
                        .issuedAt(Instant.now().minusSeconds(60))
                        .expiresAt(Instant.now().plusSeconds(300)).build();
            };
        }

        @Bean("orderJwtAuthenticationConverter")
        Converter<Jwt, AbstractAuthenticationToken> orderJwtAuthenticationConverter() {
            return new TestJwtAuthenticationConverter();
        }
    }

    static final class TestJwtAuthenticationConverter
            implements Converter<Jwt, AbstractAuthenticationToken> {
        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {
            return new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());
        }
    }
}
