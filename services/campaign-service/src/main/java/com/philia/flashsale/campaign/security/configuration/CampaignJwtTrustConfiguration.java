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
 * Builds the two JWT trust boundaries used by Campaign Service.
 *
 * <p>The public and internal decoders deliberately validate different audiences so a token
 * issued for one boundary cannot be substituted at the other boundary.</p>
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        name = "flashsale.campaign.security.jwt.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CampaignJwtTrustConfiguration {

    @Bean("campaignPublicJwtDecoder")
    JwtDecoder campaignPublicJwtDecoder(
            @Value("${flashsale.campaign.security.public.jwk-set-uri}") String jwkSetUri,
            @Value("${flashsale.campaign.security.public.issuer}") String issuer,
            @Value("${flashsale.campaign.security.public.audience}") String audience) {
        return decoder(jwkSetUri, issuer, audience);
    }

    @Bean("campaignInternalJwtDecoder")
    JwtDecoder campaignInternalJwtDecoder(
            @Value("${flashsale.campaign.security.internal.jwk-set-uri}") String jwkSetUri,
            @Value("${flashsale.campaign.security.internal.issuer}") String issuer,
            @Value("${flashsale.campaign.security.internal.audience}") String audience) {
        return decoder(jwkSetUri, issuer, audience);
    }

    private JwtDecoder decoder(String jwkSetUri, String issuer, String audience) {
        // Disable Nimbus' default type check so the approved at+jwt check is explicit below.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .validateType(false)
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                new CampaignJwtTypeValidator(),
                new CampaignJwtAudienceValidator(audience)));
        return decoder;
    }

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
