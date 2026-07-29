package com.philia.flashsale.product.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ProductJwtTrustConfigurationTests {

    private final ProductJwtTrustConfiguration.ProductJwtAudienceValidator validator =
            new ProductJwtTrustConfiguration.ProductJwtAudienceValidator("flash-sale-api");

    @Test
    void acceptsTheCanonicalAudience() {
        assertThat(validator.validate(jwtWithAudience(List.of("flash-sale-api"))).hasErrors()).isFalse();
    }

    @Test
    void rejectsADifferentAudience() {
        assertThat(validator.validate(jwtWithAudience(List.of("other-api"))).hasErrors()).isTrue();
    }

    @Test
    void rejectsAMissingSubject() {
        Instant now = Instant.now();
        Jwt token = Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .issuer("http://authentication-service:8080")
                .audience(List.of("flash-sale-api"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();

        assertThat(validator.validate(token).hasErrors()).isTrue();
    }

    @Test
    void mapsAuthIssuedAdminAuthorityAndDoesNotElevateNormalUsers() {
        var converter = new ProductAdminSecurityConfiguration().productAdminJwtAuthenticationConverter();
        Jwt admin = jwtWithAuthorities(List.of("ROLE_ADMIN", "CATALOG_ADMIN"));
        Jwt user = jwtWithAuthorities(List.of("ROLE_USER"));

        assertThat(converter.convert(admin).getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .contains("CATALOG_ADMIN");
        assertThat(converter.convert(user).getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .doesNotContain("CATALOG_ADMIN");
    }

    private Jwt jwtWithAudience(List<String> audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .subject("user-1")
                .issuer("http://authentication-service:8080")
                .audience(audience)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }

    private Jwt jwtWithAuthorities(List<String> authorities) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .header("kid", "test-key")
                .subject("user-1")
                .issuer("http://authentication-service:8080")
                .audience(List.of("flash-sale-api"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("authorities", authorities)
                .build();
    }
}
