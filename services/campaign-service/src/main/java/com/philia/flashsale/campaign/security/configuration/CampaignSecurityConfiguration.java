package com.philia.flashsale.campaign.security.configuration;

import com.philia.flashsale.campaign.websupport.error.CampaignSecurityFailureHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Separates administrator ingress from service-to-service ingress at the filter-chain boundary.
 *
 * <p>Each chain supplies the decoder for its own audience. The final chain denies unrecognised
 * application paths so adding a controller does not accidentally expose it without a rule.</p>
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CampaignSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain campaignInternalSecurityChain(
            HttpSecurity http,
            @Qualifier("campaignInternalJwtDecoder") JwtDecoder internalJwtDecoder,
            CampaignJwtAuthenticationConverter campaignJwtAuthenticationConverter,
            CampaignSecurityFailureHandler securityFailureHandler)
            throws Exception {
        return http
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        // T082 adds the exact snapshot subject and scope rule to this boundary.
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityFailureHandler)
                        .accessDeniedHandler(securityFailureHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(securityFailureHandler)
                        .accessDeniedHandler(securityFailureHandler)
                        .jwt(jwt -> jwt
                                .decoder(internalJwtDecoder)
                                .jwtAuthenticationConverter(campaignJwtAuthenticationConverter)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain campaignPublicAdminSecurityChain(
            HttpSecurity http,
            @Qualifier("campaignPublicJwtDecoder") JwtDecoder publicJwtDecoder,
            CampaignJwtAuthenticationConverter campaignJwtAuthenticationConverter,
            CampaignSecurityFailureHandler securityFailureHandler)
            throws Exception {
        return http
                .securityMatcher("/api/v1/admin/campaigns/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasAuthority("SCOPE_CAMPAIGN_ADMIN"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityFailureHandler)
                        .accessDeniedHandler(securityFailureHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(securityFailureHandler)
                        .accessDeniedHandler(securityFailureHandler)
                        .jwt(jwt -> jwt
                                .decoder(publicJwtDecoder)
                                .jwtAuthenticationConverter(campaignJwtAuthenticationConverter)))
                .build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain campaignDenyByDefaultSecurityChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus")
                        .permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    CampaignJwtAuthenticationConverter campaignJwtAuthenticationConverter() {
        return new CampaignJwtAuthenticationConverter();
    }

    /** Converts supported authority and scope claims into Spring Security authorities. */
    static final class CampaignJwtAuthenticationConverter
            implements Converter<Jwt, AbstractAuthenticationToken> {

        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            addAuthorities(authorities, jwt.getClaim("authorities"));
            addAuthorities(authorities, jwt.getClaim("roles"));
            addScopes(authorities, jwt.getClaim("scope"));
            addScopes(authorities, jwt.getClaim("scp"));
            return new JwtAuthenticationToken(jwt, authorities);
        }

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

        private void addScopes(List<GrantedAuthority> authorities, Object claim) {
            if (claim instanceof String value) {
                for (String scope : value.split(" ")) {
                    addScope(authorities, scope);
                }
            }
            if (claim instanceof Collection<?> values) {
                values.stream().map(String::valueOf).forEach(scope -> addScope(authorities, scope));
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
