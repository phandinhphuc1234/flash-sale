package com.philia.flashsale.inventory.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class InventoryJwtTrustConfigurationTests {

    private final InventoryJwtTrustConfiguration.InventoryJwtAudienceValidator audienceValidator =
            new InventoryJwtTrustConfiguration.InventoryJwtAudienceValidator("flash-sale-api");
    private final InventoryJwtTrustConfiguration.InventoryJwtTypeValidator typeValidator =
            new InventoryJwtTrustConfiguration.InventoryJwtTypeValidator();

    @Test
    void acceptsTheCanonicalAudienceAndAccessTokenType() {
        Jwt token = jwt("user-1", List.of("flash-sale-api"), "at+jwt");

        assertThat(audienceValidator.validate(token).hasErrors()).isFalse();
        assertThat(typeValidator.validate(token).hasErrors()).isFalse();
    }

    @Test
    void rejectsWrongAudienceMissingSubjectAndWrongType() {
        assertThat(audienceValidator.validate(jwt("user-1", List.of("other-api"), "at+jwt")).hasErrors())
                .isTrue();
        assertThat(audienceValidator.validate(jwt(null, List.of("flash-sale-api"), "at+jwt")).hasErrors())
                .isTrue();
        assertThat(typeValidator.validate(jwt("user-1", List.of("flash-sale-api"), "JWT")).hasErrors())
                .isTrue();
    }

    private Jwt jwt(String subject, List<String> audience, String type) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .header("typ", type)
                .issuer("http://authentication-service:8080")
                .audience(audience)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }
}
