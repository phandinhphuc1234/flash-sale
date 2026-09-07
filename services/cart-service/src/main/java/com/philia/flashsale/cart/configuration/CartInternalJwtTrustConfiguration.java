package com.philia.flashsale.cart.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Creates a separate decoder so a browser JWT can never satisfy the Order machine boundary. */
@Configuration
@EnableConfigurationProperties(CartInternalJwtProperties.class)
public class CartInternalJwtTrustConfiguration {

    @Bean("cartOrderInternalJwtDecoder")
    @ConditionalOnMissingBean(name = "cartOrderInternalJwtDecoder")
    JwtDecoder cartOrderInternalJwtDecoder(CartInternalJwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .validateType(false)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                token -> containsAudience(token, properties.audience()),
                token -> properties.subject().equals(token.getSubject())
                        ? OAuth2TokenValidatorResult.success()
                        : failure("Required internal subject is missing"),
                token -> "at+jwt".equals(token.getHeaders().get("typ"))
                        ? OAuth2TokenValidatorResult.success()
                        : failure("Required JWT type is missing")));
        return decoder;
    }

    private static OAuth2TokenValidatorResult containsAudience(Jwt token, String audience) {
        return token.getAudience() != null && token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : failure("Required internal audience is missing");
    }

    private static OAuth2TokenValidatorResult failure(String message) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", message, null));
    }
}
