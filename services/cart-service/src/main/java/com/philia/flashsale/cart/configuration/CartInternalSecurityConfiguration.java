package com.philia.flashsale.cart.configuration;

import com.philia.flashsale.cart.security.CartSecurityErrorHandler;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/** Guards only the future Order-to-Cart snapshot endpoint with Order's exact machine capability. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CartInternalSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain cartOrderInternalSecurityChain(HttpSecurity http,
            @Qualifier("cartOrderInternalJwtDecoder") JwtDecoder decoder,
            CartInternalJwtProperties properties,
            CartSecurityErrorHandler errorHandler) throws Exception {
        return http.securityMatcher("/internal/v1/cart-checkout-snapshots")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest()
                        .access((authentication, context) -> authorize(authentication, properties)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(scopeConverter(properties))))
                .build();
    }

    static AuthorizationDecision authorize(Supplier<Authentication> supplier,
            CartInternalJwtProperties properties) {
        Authentication authentication = supplier.get();
        boolean subjectMatches = authentication != null && authentication.getPrincipal() instanceof Jwt jwt
                && properties.subject().equals(jwt.getSubject());
        boolean scopeMatches = authentication != null && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(("SCOPE_" + properties.requiredScope())::equals);
        return new AuthorizationDecision(subjectMatches && scopeMatches);
    }

    static JwtAuthenticationConverter scopeConverter(CartInternalJwtProperties properties) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            if (!properties.subject().equals(jwt.getSubject())) return List.of();
            Object claim = jwt.getClaims().get("scope");
            if (!(claim instanceof String scopes)) return List.of();
            return List.of(scopes.split(" ")).stream().filter(scope -> !scope.isBlank())
                    .<GrantedAuthority>map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope.trim()))
                    .toList();
        });
        return converter;
    }
}
