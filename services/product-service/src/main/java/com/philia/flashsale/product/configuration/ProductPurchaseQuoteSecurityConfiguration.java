package com.philia.flashsale.product.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/** Isolates the future Order purchase-quote path from Cart and Campaign internal permissions. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ProductPurchaseQuoteSecurityConfiguration {

    @Bean
    @Order(3)
    SecurityFilterChain productOrderPurchaseQuoteSecurityFilterChain(HttpSecurity http,
            @Qualifier("productOrderInternalJwtDecoder") JwtDecoder decoder,
            ProductOrderInternalJwtProperties properties,
            ProductInternalSecurityFailureHandler failureHandler) throws Exception {
        return http.securityMatcher("/internal/v1/catalog/variants/purchase-quotes")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest()
                        .access((authentication, context) -> authorize(authentication, properties)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(failureHandler)
                        .accessDeniedHandler(failureHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(failureHandler)
                        .accessDeniedHandler(failureHandler)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(scopeConverter(properties))))
                .build();
    }

    static AuthorizationDecision authorize(Supplier<Authentication> supplier,
            ProductOrderInternalJwtProperties properties) {
        Authentication authentication = supplier.get();
        boolean subjectMatches = authentication != null && authentication.getPrincipal() instanceof Jwt jwt
                && properties.subject().equals(jwt.getSubject());
        boolean scopeMatches = authentication != null && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(("SCOPE_" + properties.requiredScope())::equals);
        return new AuthorizationDecision(subjectMatches && scopeMatches);
    }

    static JwtAuthenticationConverter scopeConverter(ProductOrderInternalJwtProperties properties) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new ScopeConverter(properties.subject()));
        return converter;
    }

    private static final class ScopeConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        private final String subject;

        private ScopeConverter(String subject) { this.subject = subject; }

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            if (!subject.equals(jwt.getSubject())) return List.of();
            Object claim = jwt.getClaims().get("scope");
            if (!(claim instanceof String scopes)) return List.of();
            List<GrantedAuthority> authorities = new ArrayList<>();
            for (String scope : scopes.split(" ")) {
                if (!scope.isBlank()) authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope.trim()));
            }
            return authorities;
        }
    }
}
