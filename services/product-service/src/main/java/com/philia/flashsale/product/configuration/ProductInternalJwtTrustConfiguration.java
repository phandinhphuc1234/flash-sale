package com.philia.flashsale.product.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
@EnableConfigurationProperties(ProductInternalJwtProperties.class)
public class ProductInternalJwtTrustConfiguration {
    @Bean(name = "productInternalJwtDecoder")
    @ConditionalOnMissingBean(name = "productInternalJwtDecoder")
    JwtDecoder productInternalJwtDecoder(ProductInternalJwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .validateType(false)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                token -> has(token, "aud", properties.audience(), "Required internal audience is missing"),
                token -> has(token, "sub", properties.subject(), "Required internal subject is missing"),
                token -> "at+jwt".equals(token.getHeaders().get("typ"))
                        ? OAuth2TokenValidatorResult.success()
                        : failure("Required JWT type is missing")));
        return decoder;
    }

    private static OAuth2TokenValidatorResult has(Jwt token, String claim, String expected, String message) {
        boolean valid = "aud".equals(claim)
                ? token.getAudience() != null && token.getAudience().contains(expected)
                : expected.equals(token.getSubject());
        return valid ? OAuth2TokenValidatorResult.success() : failure(message);
    }

    private static OAuth2TokenValidatorResult failure(String message) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", message, null));
    }
}
