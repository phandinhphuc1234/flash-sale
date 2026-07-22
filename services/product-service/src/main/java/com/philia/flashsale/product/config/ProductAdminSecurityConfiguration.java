package com.philia.flashsale.product.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.philia.flashsale.product.adapter.in.web.admin.ProductAdminSecurityFailureHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ProductAdminSecurityConfiguration {

    @Bean
    // Product-service rechecks authorization even when requests already passed through api-gateway.
    SecurityFilterChain productAdminSecurityFilterChain(
            HttpSecurity http,
            ProductAdminSecurityFailureHandler securityFailureHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        // Public catalog stays open; admin catalog requires the CATALOG_ADMIN authority.
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/v1/catalog/**").permitAll()
                        .requestMatchers("/api/v1/admin/catalog/**").hasAuthority("CATALOG_ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityFailureHandler)
                        .accessDeniedHandler(securityFailureHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(securityFailureHandler)
                        .accessDeniedHandler(securityFailureHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                productAdminJwtAuthenticationConverter())))
                .build();
    }

    @Bean
    // Normalize JWT claims from different issuers into Spring Security authorities.
    JwtAuthenticationConverter productAdminJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new CatalogAdminAuthorityConverter());
        return converter;
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

        // Accept both role-style claims and authority-style claims without coupling callers to one issuer format.
        private void addAuthorities(List<GrantedAuthority> authorities, Object claim) {
            if (claim instanceof Collection<?> values) {
                values.stream()
                        .map(String::valueOf)
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }
        }

        // Scope claims are represented with Spring's SCOPE_ prefix to keep OAuth semantics explicit.
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
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + normalized));
            }
        }
    }
}
