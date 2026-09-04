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
@EnableConfigurationProperties({
        ProductInternalJwtProperties.class,
        ProductCartInternalJwtProperties.class,
        ProductOrderInternalJwtProperties.class
})
public class ProductInternalJwtTrustConfiguration {
    @Bean(name = "productInternalJwtDecoder")
    @ConditionalOnMissingBean(name = "productInternalJwtDecoder")
    JwtDecoder productInternalJwtDecoder(ProductInternalJwtProperties properties) {
        return decoder(properties.issuer(), properties.jwkSetUri(), properties.audience(), properties.subject());
    }

    @Bean(name = "productCartInternalJwtDecoder")
    @ConditionalOnMissingBean(name = "productCartInternalJwtDecoder")
    JwtDecoder productCartInternalJwtDecoder(ProductCartInternalJwtProperties properties) {
        return decoder(properties.issuer(), properties.jwkSetUri(), properties.audience(), properties.subject());
    }

    @Bean(name = "productOrderInternalJwtDecoder")
    @ConditionalOnMissingBean(name = "productOrderInternalJwtDecoder")
    JwtDecoder productOrderInternalJwtDecoder(ProductOrderInternalJwtProperties properties) {
        return decoder(properties.issuer(), properties.jwkSetUri(), properties.audience(), properties.subject());
    }

    private JwtDecoder decoder(String issuer, String jwkSetUri, String audience, String subject) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .validateType(false)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                token -> has(token, "aud", audience, "Required internal audience is missing"),
                token -> has(token, "sub", subject, "Required internal subject is missing"),
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
