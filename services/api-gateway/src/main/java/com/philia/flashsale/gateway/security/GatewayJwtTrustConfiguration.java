package com.philia.flashsale.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/** Builds the Gateway decoder with the repository-wide issuer and audience contract. */
@Configuration
@ConditionalOnProperty(
        name = "flashsale.gateway.security.jwt.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class GatewayJwtTrustConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReactiveJwtDecoder.class)
    ReactiveJwtDecoder gatewayJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${flashsale.gateway.security.jwt.audience}") String audience) {
        // Auth issues the approved RFC 9068-style `at+jwt` type; disable Spring's
        // default `typ=JWT` check and enforce the repository contract below.
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                .validateType(false)
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                new GatewayJwtTypeValidator(),
                new GatewayJwtAudienceValidator(audience)));
        return decoder;
    }

    static final class GatewayJwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

        private final String requiredAudience;

        GatewayJwtAudienceValidator(String requiredAudience) {
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

    static final class GatewayJwtTypeValidator implements OAuth2TokenValidator<Jwt> {
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
