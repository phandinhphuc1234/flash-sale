package com.philia.flashsale.product.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Builds the Product resource-server decoder with explicit issuer and audience validation. */
@Configuration
@ConditionalOnProperty(
        name = "flashsale.product.security.jwt.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ProductJwtTrustConfiguration {

    @Primary
    @ConditionalOnMissingBean(name = "productJwtDecoder")
    @Bean(name = "productJwtDecoder")
    JwtDecoder productJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${flashsale.product.security.jwt.audience}") String audience) {
        // Auth issues the approved `at+jwt` type, so enforce it explicitly while
        // retaining normal signature, issuer, audience, and time validation.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .validateType(false)
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                new ProductJwtTypeValidator(),
                new ProductJwtAudienceValidator(audience)));
        return decoder;
    }

    static final class ProductJwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

        private final String requiredAudience;

        ProductJwtAudienceValidator(String requiredAudience) {
            this.requiredAudience = requiredAudience;
        }

        @Override
        public org.springframework.security.oauth2.core.OAuth2TokenValidatorResult validate(Jwt token) {
            return token.getAudience() != null
                    && token.getAudience().contains(requiredAudience)
                    && token.getSubject() != null
                    && !token.getSubject().isBlank()
                    ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                    : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                            new org.springframework.security.oauth2.core.OAuth2Error(
                                    "invalid_token", "Required JWT audience is missing", null));
        }
    }

    static final class ProductJwtTypeValidator implements OAuth2TokenValidator<Jwt> {
        @Override
        public org.springframework.security.oauth2.core.OAuth2TokenValidatorResult validate(Jwt token) {
            return "at+jwt".equals(token.getHeaders().get("typ"))
                    ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                    : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                            new org.springframework.security.oauth2.core.OAuth2Error(
                                    "invalid_token", "Required JWT type is missing", null));
        }
    }
}
