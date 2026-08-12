package com.philia.flashsale.inventory.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@EnableConfigurationProperties(InventoryInternalJwtProperties.class)
public class InventoryInternalJwtTrustConfiguration {
    @Bean(name = "inventoryInternalJwtDecoder")
    JwtDecoder inventoryInternalJwtDecoder(InventoryInternalJwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .validateType(false)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                token -> audience(token, properties.audience()),
                token -> subject(token, properties.subject()),
                token -> "at+jwt".equals(token.getHeaders().get("typ"))
                        ? OAuth2TokenValidatorResult.success()
                        : failure("Required JWT type is missing")));
        return decoder;
    }

    private static OAuth2TokenValidatorResult audience(Jwt token, String expected) {
        return token.getAudience() != null && token.getAudience().contains(expected)
                ? OAuth2TokenValidatorResult.success() : failure("Required internal audience is missing");
    }

    private static OAuth2TokenValidatorResult subject(Jwt token, String expected) {
        return expected.equals(token.getSubject())
                ? OAuth2TokenValidatorResult.success() : failure("Required internal subject is missing");
    }

    private static OAuth2TokenValidatorResult failure(String message) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", message, null));
    }
}
