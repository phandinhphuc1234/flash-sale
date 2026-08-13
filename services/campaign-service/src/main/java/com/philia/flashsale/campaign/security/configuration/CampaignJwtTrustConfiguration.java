package com.philia.flashsale.campaign.security.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
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

/**
 * Configures two separate JWT trust boundaries (Public & Internal) for Campaign
 * Service.
 * 
 * PURPOSE: Ensures tokens issued for public/external APIs cannot be misused for
 * internal microservice communication and vice versa.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "flashsale.campaign.security.jwt.enabled", havingValue = "true", matchIfMissing = true)
public class CampaignJwtTrustConfiguration {

    /**
     * Creates a JwtDecoder dedicated to validating JWT tokens sent from
     * public/external user requests.
     */
    @Bean("campaignPublicJwtDecoder")
    JwtDecoder campaignPublicJwtDecoder(
            @Value("${flashsale.campaign.security.public.jwk-set-uri}") String jwkSetUri,
            @Value("${flashsale.campaign.security.public.issuer}") String issuer,
            @Value("${flashsale.campaign.security.public.audience}") String audience) {
        return decoder(jwkSetUri, issuer, audience);
    }

    /**
     * Creates a JwtDecoder dedicated to validating JWT tokens used in internal
     * service-to-service communication.
     */
    @Bean("campaignInternalJwtDecoder")
    JwtDecoder campaignInternalJwtDecoder(
            @Value("${flashsale.campaign.security.internal.jwk-set-uri}") String jwkSetUri,
            @Value("${flashsale.campaign.security.internal.issuer}") String issuer,
            @Value("${flashsale.campaign.security.internal.audience}") String audience) {
        return decoder(jwkSetUri, issuer, audience);
    }

    /**
     * Common helper method to build NimbusJwtDecoder with JWK Set URI and attach
     * custom validators (Issuer, Type, Audience).
     */
    private JwtDecoder decoder(String jwkSetUri, String issuer, String audience) {
        // Build NimbusJwtDecoder and disable default type check to apply explicit
        // custom validations below
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .validateType(false)
                .build();

        // Issuer validator (validates token issuer authority)
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);

        // Chain required validators together: Issuer + Type (at+jwt) + Audience
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                new CampaignJwtTypeValidator(),
                new CampaignJwtAudienceValidator(audience)));

        return decoder;
    }

    /**
     * Custom validator: Ensures the JWT contains the required 'aud' (Audience)
     * claim matching the boundary configuration.
     */
    static final class CampaignJwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

        private final String requiredAudience;

        CampaignJwtAudienceValidator(String requiredAudience) {
            this.requiredAudience = requiredAudience;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            return token.getAudience() != null
                    && token.getAudience().contains(requiredAudience)
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                    "invalid_token", "Required JWT audience is missing", null));
        }
    }

    /**
     * Custom validator: Ensures the JWT Header 'typ' matches the standard 'at+jwt'
     * (Access Token JWT) format.
     */
    static final class CampaignJwtTypeValidator implements OAuth2TokenValidator<Jwt> {

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            return "at+jwt".equals(token.getHeaders().get("typ"))
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                            "invalid_token", "Required JWT type is missing", null));
        }
    }
}