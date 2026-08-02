package com.philia.flashsale.product.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ProductInternalSecurityConfiguration {
    @Bean
    @Order(1)
    SecurityFilterChain productInternalSecurityFilterChain(HttpSecurity http,
            @Qualifier("productInternalJwtDecoder") JwtDecoder productInternalJwtDecoder,
            ProductInternalJwtProperties properties,
            ProductInternalSecurityFailureHandler failureHandler) throws Exception {
        return http.securityMatcher("/internal/v1/catalog/variants/campaign-validation")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().access((authentication, context) -> authorizeInternal(authentication, properties)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(failureHandler)
                        .accessDeniedHandler(failureHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(failureHandler)
                        .accessDeniedHandler(failureHandler)
                        .jwt(jwt -> jwt.decoder(productInternalJwtDecoder)
                                .jwtAuthenticationConverter(internalJwtAuthenticationConverter(properties))))
                .build();
    }

    private AuthorizationDecision authorizeInternal(java.util.function.Supplier<Authentication> authentication,
            ProductInternalJwtProperties properties) {
        Authentication current = authentication.get();
        boolean subjectMatches = current.getPrincipal() instanceof Jwt jwt
                && properties.subject().equals(jwt.getSubject());
        boolean scopeMatches = current.getAuthorities().stream()
                .anyMatch(authority -> ("SCOPE_" + properties.requiredScope()).equals(authority.getAuthority()));
        return new AuthorizationDecision(subjectMatches && scopeMatches);
    }

    @Bean
    JwtAuthenticationConverter internalJwtAuthenticationConverter(ProductInternalJwtProperties properties) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new InternalScopeConverter(properties.subject()));
        return converter;
    }

    private static final class InternalScopeConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        private final String requiredSubject;
        private InternalScopeConverter(String requiredSubject) { this.requiredSubject = requiredSubject; }
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            if (!requiredSubject.equals(jwt.getSubject())) return List.of();
            List<GrantedAuthority> authorities = new ArrayList<>();
            Object claim = jwt.getClaims().get("scope");
            if (claim instanceof String value) {
                for (String scope : value.split(" ")) add(authorities, scope);
            } else if (claim instanceof Collection<?> values) {
                values.forEach(scope -> add(authorities, String.valueOf(scope)));
            }
            return authorities;
        }
        private void add(List<GrantedAuthority> authorities, String scope) {
            if (scope != null && !scope.isBlank()) authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope.trim()));
        }
    }
}
