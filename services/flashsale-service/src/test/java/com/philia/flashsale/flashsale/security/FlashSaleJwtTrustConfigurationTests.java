package com.philia.flashsale.flashsale.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

class FlashSaleJwtTrustConfigurationTests {
    private static final String ISSUER = "https://auth.example.test";
    private static final String AUDIENCE = "flash-sale-api";
    private static final String SUBJECT = "7cc05861-c4bf-40f0-adde-ab6ca74e0e83";

    @Test
    void acceptsApprovedIssuerAudienceTypeAndUuidSubject() {
        Jwt jwt = jwt(AUDIENCE, "at+jwt", SUBJECT);
        assertTrue(!JwtValidators.createDefaultWithIssuer(ISSUER).validate(jwt).hasErrors());
        assertTrue(!new FlashSaleJwtTrustConfiguration.JwtTypeValidator().validate(jwt).hasErrors());
        assertTrue(!new FlashSaleJwtTrustConfiguration.JwtAudienceValidator(AUDIENCE).validate(jwt).hasErrors());
        assertTrue(!new FlashSaleJwtTrustConfiguration.JwtSubjectValidator().validate(jwt).hasErrors());
    }

    @Test
    void rejectsWrongAudienceTypeAndSubject() {
        assertTrue(new FlashSaleJwtTrustConfiguration.JwtAudienceValidator(AUDIENCE)
                .validate(jwt("other-api", "at+jwt", SUBJECT)).hasErrors());
        assertTrue(new FlashSaleJwtTrustConfiguration.JwtTypeValidator()
                .validate(jwt(AUDIENCE, "JWT", SUBJECT)).hasErrors());
        assertTrue(new FlashSaleJwtTrustConfiguration.JwtSubjectValidator()
                .validate(jwt(AUDIENCE, "at+jwt", "not-a-uuid")).hasErrors());
    }

    private Jwt jwt(String audience, String type, String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .header("typ", type)
                .issuer(ISSUER)
                .audience(List.of(audience))
                .subject(subject)
                .issuedAt(now.minusSeconds(10))
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
