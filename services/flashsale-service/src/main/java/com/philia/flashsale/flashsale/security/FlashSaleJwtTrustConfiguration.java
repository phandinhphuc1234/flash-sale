package com.philia.flashsale.flashsale.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Builds the independent public JWT trust boundary for shopper requests. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "flashsale.security.jwt.enabled", havingValue = "true", matchIfMissing = true)
public class FlashSaleJwtTrustConfiguration {

    @Bean("flashSaleJwtDecoder")
    JwtDecoder flashSaleJwtDecoder(
            @Value("${flashsale.security.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${flashsale.security.jwt.issuer}") String issuer,
            @Value("${flashsale.security.jwt.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .validateType(false)
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                new JwtTypeValidator(),
                new JwtAudienceValidator(audience),
                new JwtSubjectValidator()));
        return decoder;
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> flashSaleJwtAuthenticationConverter() {
        return new FlashSaleJwtAuthenticationConverter();
    }

    /** Explicit generic converter type lets Spring Security resolve the token boundary reliably. */
    static final class FlashSaleJwtAuthenticationConverter
            implements Converter<Jwt, AbstractAuthenticationToken> {
        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {
            List<GrantedAuthority> authorities = new java.util.ArrayList<>();
            addAuthorities(authorities, jwt.getClaim("authorities"));
            addAuthorities(authorities, jwt.getClaim("roles"));
            addScopes(authorities, jwt.getClaim("scope"));
            addScopes(authorities, jwt.getClaim("scp"));
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        }
    }

    private static void addAuthorities(List<GrantedAuthority> authorities, Object claim) {
        if (claim instanceof Collection<?> values) {
            values.stream().map(String::valueOf).map(String::trim)
                    .filter(value -> !value.isBlank())
                    .forEach(value -> authorities.add(new SimpleGrantedAuthority(value)));
        }
    }

    private static void addScopes(List<GrantedAuthority> authorities, Object claim) {
        if (claim instanceof String value) {
            for (String scope : value.split(" ")) {
                if (!scope.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
                }
            }
        }
        if (claim instanceof Collection<?> values) {
            values.stream().map(String::valueOf).map(String::trim)
                    .filter(value -> !value.isBlank())
                    .forEach(value -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + value)));
        }
    }

    static final class JwtTypeValidator implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            return "at+jwt".equals(token.getHeaders().get("typ"))
                    ? OAuth2TokenValidatorResult.success()
                    : failure("Required JWT type is missing");
        }
    }

    static final class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String audience;

        JwtAudienceValidator(String audience) {
            this.audience = audience;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            return token.getAudience() != null && token.getAudience().contains(audience)
                    ? OAuth2TokenValidatorResult.success()
                    : failure("Required JWT audience is missing");
        }
    }

    static final class JwtSubjectValidator implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            try {
                UUID.fromString(token.getSubject());
                return OAuth2TokenValidatorResult.success();
            } catch (RuntimeException ex) {
                return failure("JWT subject must be a UUID");
            }
        }
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
