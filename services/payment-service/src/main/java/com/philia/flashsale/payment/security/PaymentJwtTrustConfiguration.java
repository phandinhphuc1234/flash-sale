package com.philia.flashsale.payment.security;

import com.philia.flashsale.payment.configuration.PaymentProperties;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
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

/** Local Payment JWT trust boundary with issuer, audience, type, subject, and expiry checks. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "payment.acceptance.enabled", havingValue = "true")
public class PaymentJwtTrustConfiguration {

    @Bean("paymentJwtDecoder")
    JwtDecoder paymentJwtDecoder(PaymentProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwtJwkSetUri())
                .validateType(false).build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.jwtIssuer());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer,
                new TypeValidator(properties.jwtType()), new AudienceValidator(properties.jwtAudience()),
                new SubjectValidator()));
        return decoder;
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> paymentJwtAuthenticationConverter() {
        return new PaymentJwtAuthenticationConverter();
    }

    /** Named generic converter keeps Spring's conversion service type metadata intact. */
    static final class PaymentJwtAuthenticationConverter
            implements Converter<Jwt, AbstractAuthenticationToken> {
        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {
            return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
        }
    }

    private static List<GrantedAuthority> authorities(Jwt jwt) {
        List<GrantedAuthority> values = new java.util.ArrayList<>();
        addCollection(values, jwt.getClaim("authorities"));
        addCollection(values, jwt.getClaim("roles"));
        addScope(values, jwt.getClaim("scope"));
        addScope(values, jwt.getClaim("scp"));
        return values;
    }

    private static void addCollection(List<GrantedAuthority> values, Object claim) {
        if (claim instanceof Collection<?> collection) {
            collection.stream().map(String::valueOf).map(String::trim).filter(value -> !value.isBlank())
                    .map(SimpleGrantedAuthority::new).forEach(values::add);
        }
    }

    private static void addScope(List<GrantedAuthority> values, Object claim) {
        if (claim instanceof String text) {
            for (String scope : text.split(" ")) {
                if (!scope.isBlank()) values.add(new SimpleGrantedAuthority("SCOPE_" + scope));
            }
        }
    }

    static final class TypeValidator implements OAuth2TokenValidator<Jwt> {
        private final String expected;
        TypeValidator(String expected) { this.expected = expected; }
        @Override public OAuth2TokenValidatorResult validate(Jwt token) {
            return expected.equals(token.getHeaders().get("typ")) ? OAuth2TokenValidatorResult.success()
                    : failure("Required JWT type is missing");
        }
    }

    static final class AudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String expected;
        AudienceValidator(String expected) { this.expected = expected; }
        @Override public OAuth2TokenValidatorResult validate(Jwt token) {
            return token.getAudience() != null && token.getAudience().contains(expected)
                    ? OAuth2TokenValidatorResult.success() : failure("Required JWT audience is missing");
        }
    }

    static final class SubjectValidator implements OAuth2TokenValidator<Jwt> {
        @Override public OAuth2TokenValidatorResult validate(Jwt token) {
            return token.getSubject() != null && !token.getSubject().isBlank()
                    ? OAuth2TokenValidatorResult.success() : failure("JWT subject is missing");
        }
    }

    private static OAuth2TokenValidatorResult failure(String message) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", message, null));
    }
}
