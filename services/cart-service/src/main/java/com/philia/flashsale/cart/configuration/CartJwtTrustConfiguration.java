package com.philia.flashsale.cart.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Revalidates the public shopper JWT inside Cart instead of trusting the Gateway alone. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "cart.security.jwt.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CartJwtProperties.class)
public class CartJwtTrustConfiguration {

    @Bean("cartJwtDecoder")
    JwtDecoder cartJwtDecoder(CartJwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .validateType(false)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtTypeValidator(properties.type()),
                new JwtAudienceValidator(properties.audience()),
                new JwtSubjectValidator()));
        return decoder;
    }

    @Bean("cartJwtAuthenticationConverter")
    Converter<Jwt, AbstractAuthenticationToken> cartJwtAuthenticationConverter() {
        return jwt -> new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
    }

    private static List<GrantedAuthority> authorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        addCollection(authorities, jwt.getClaim("authorities"));
        addCollection(authorities, jwt.getClaim("roles"));
        addScopes(authorities, jwt.getClaim("scope"));
        addScopes(authorities, jwt.getClaim("scp"));
        return authorities;
    }

    private static void addCollection(List<GrantedAuthority> authorities, Object claim) {
        if (claim instanceof Collection<?> values) {
            values.stream().map(String::valueOf).map(String::trim).filter(value -> !value.isBlank())
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
        } else if (claim instanceof Collection<?> values) {
            values.stream().map(String::valueOf).map(String::trim).filter(value -> !value.isBlank())
                    .forEach(value -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + value)));
        }
    }

    static final class JwtTypeValidator implements OAuth2TokenValidator<Jwt> {
        private final String expected;

        JwtTypeValidator(String expected) {
            this.expected = expected;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            return expected.equals(token.getHeaders().get("typ"))
                    ? OAuth2TokenValidatorResult.success() : failure("Required JWT type is missing");
        }
    }

    static final class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String expected;

        JwtAudienceValidator(String expected) {
            this.expected = expected;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            return token.getAudience() != null && token.getAudience().contains(expected)
                    ? OAuth2TokenValidatorResult.success() : failure("Required JWT audience is missing");
        }
    }

    static final class JwtSubjectValidator implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            return token.getSubject() != null && !token.getSubject().isBlank()
                    ? OAuth2TokenValidatorResult.success() : failure("JWT subject is missing");
        }
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
