package com.philia.flashsale.order.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

class OrderJwtTrustConfigurationTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void acceptsTheApprovedTypeAudienceAndNonBlankSubject() {
        Jwt token = token("owner-001", "at+jwt", List.of("flash-sale-api"));

        assertThat(new OrderJwtTrustConfiguration.JwtTypeValidator("at+jwt").validate(token).hasErrors())
                .isFalse();
        assertThat(new OrderJwtTrustConfiguration.JwtAudienceValidator("flash-sale-api")
                .validate(token).hasErrors()).isFalse();
        assertThat(new OrderJwtTrustConfiguration.JwtSubjectValidator().validate(token).hasErrors()).isFalse();
        assertThat(JwtValidators.createDefaultWithIssuer("issuer").validate(tokenWithIssuer("issuer"))
                .hasErrors()).isFalse();
    }

    @Test
    void rejectsWrongTypeAudienceAndMissingSubject() {
        assertThat(new OrderJwtTrustConfiguration.JwtTypeValidator("at+jwt")
                .validate(token("owner-001", "id+jwt", List.of("flash-sale-api"))).hasErrors()).isTrue();
        assertThat(new OrderJwtTrustConfiguration.JwtAudienceValidator("flash-sale-api")
                .validate(token("owner-001", "at+jwt", List.of("other-api"))).hasErrors()).isTrue();
        assertThat(new OrderJwtTrustConfiguration.JwtSubjectValidator()
                .validate(token("", "at+jwt", List.of("flash-sale-api"))).hasErrors()).isTrue();
        assertThat(new JwtTimestampValidator().validate(
                tokenWithTimes(Instant.now().minusSeconds(1200), Instant.now().minusSeconds(600)))
                .hasErrors()).isTrue();
        assertThat(JwtValidators.createDefaultWithIssuer("issuer").validate(
                tokenWithIssuer("other-issuer")).hasErrors()).isTrue();
    }

    private static Jwt token(String subject, String type, List<String> audience) {
        return Jwt.withTokenValue("token").header("alg", "RS256").header("typ", type)
                .issuer("issuer").subject(subject).audience(audience).issuedAt(NOW.minusSeconds(60))
                .expiresAt(NOW.plusSeconds(300)).build();
    }

    private static Jwt tokenWithIssuer(String issuer) {
        return Jwt.withTokenValue("token").header("alg", "RS256").header("typ", "at+jwt")
                .issuer(issuer).subject("owner-001").audience(List.of("flash-sale-api"))
                .issuedAt(NOW.minusSeconds(60)).expiresAt(NOW.plusSeconds(300)).build();
    }

    private static Jwt tokenWithTimes(Instant issuedAt, Instant expiresAt) {
        return Jwt.withTokenValue("token").header("alg", "RS256").header("typ", "at+jwt")
                .issuer("issuer").subject("owner-001").audience(List.of("flash-sale-api"))
                .issuedAt(issuedAt).expiresAt(expiresAt).build();
    }
}
