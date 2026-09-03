package com.philia.flashsale.gateway.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfiguration {

    @Bean
    SecurityWebFilterChain gatewaySecurityWebFilterChain(
            ServerHttpSecurity http,
            GatewaySecurityErrorHandler securityErrorHandler,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled) {
        // Gateway is the first public security boundary; product-service still revalidates admin access.
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(authorize -> authorize
                        // Shopper catalog is intentionally public, but catalog administration is privileged.
                        .pathMatchers(
                                "/actuator",
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness",
                                "/actuator/info",
                                "/actuator/prometheus")
                        .permitAll()
                        .pathMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/v3/api-docs.yaml", "/openapi/**")
                        .access((authentication, context) -> documentationAccess(apiDocsEnabled))
                        .pathMatchers("/api/v1/catalog/**").permitAll()
                        .pathMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        .pathMatchers("/api/v1/auth/logout-all").authenticated()
                        .pathMatchers("/api/v1/admin/catalog/**").hasAuthority("CATALOG_ADMIN")
                        .pathMatchers("/api/v1/admin/inventory/**").hasAuthority("INVENTORY_ADMIN")
                        .pathMatchers("/api/v1/admin/campaigns/**").hasAuthority("SCOPE_CAMPAIGN_ADMIN")
                        .pathMatchers("/api/v1/orders/**").authenticated()
                        .pathMatchers("/api/v1/flash-sales/**").authenticated()
                        // Cart has an exact collection endpoint as well as item sub-resources.
                        // Match both forms so /api/v1/cart is not denied by the fallback chain.
                        .pathMatchers("/api/v1/cart", "/api/v1/cart/**").authenticated()
                        .pathMatchers(HttpMethod.POST, "/webhooks/v1/payments/stripe").permitAll()
                        .pathMatchers("/api/v1/payments/**").authenticated()
                        .anyExchange().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                gatewayJwtAuthenticationConverter())))
                .build();
    }

    static Mono<AuthorizationDecision> documentationAccess(boolean apiDocsEnabled) {
        return Mono.just(new AuthorizationDecision(apiDocsEnabled));
    }

    @Bean
    ReactiveJwtAuthenticationConverterAdapter gatewayJwtAuthenticationConverter() {
        // Normalize JWT role/authority claims before Spring Security checks route permissions.
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new CatalogAdminAuthorityConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    private static final class CatalogAdminAuthorityConverter
            implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            addAuthorities(authorities, jwt.getClaim("authorities"));
            addAuthorities(authorities, jwt.getClaim("roles"));
            addScopes(authorities, jwt.getClaim("scope"));
            addScopes(authorities, jwt.getClaim("scp"));
            return authorities;
        }

        private void addAuthorities(List<GrantedAuthority> authorities, Object claim) {
            if (claim instanceof Collection<?> values) {
                values.stream()
                        .map(String::valueOf)
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .forEach(value -> addAuthority(authorities, value));
            }
        }

        private void addAuthority(List<GrantedAuthority> authorities, String authority) {
            authorities.add(new SimpleGrantedAuthority(authority));
            // Auth keeps administrator capabilities in its canonical authorities claim. Campaign
            // administration uses the OAuth-style name approved by Feature 017 at both boundaries.
            if ("CAMPAIGN_ADMIN".equals(authority)) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_CAMPAIGN_ADMIN"));
            }
        }

        private void addScopes(List<GrantedAuthority> authorities, Object claim) {
            if (claim instanceof String value) {
                for (String scope : value.split(" ")) {
                    addScope(authorities, scope);
                }
            }
            if (claim instanceof Collection<?> values) {
                values.stream()
                        .map(String::valueOf)
                        .forEach(scope -> addScope(authorities, scope));
            }
        }

        private void addScope(List<GrantedAuthority> authorities, String scope) {
            String normalized = scope == null ? "" : scope.trim();
            if (!normalized.isBlank()) {
                // Spring treats OAuth scopes as SCOPE_* authorities.
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + normalized));
            }
        }
    }
}
