package com.philia.flashsale.authentication.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Keeps public infrastructure endpoints reachable while the feature-specific endpoint rules are
 * added. Business endpoints are deny-by-default until their approved authentication rule is wired.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
/** Defines stateless endpoint authorization and optional local JWT resource-server support. */
public class AuthenticationSecurityConfiguration {

    @Bean
    SecurityFilterChain authenticationSecurityFilterChain(HttpSecurity http,
            ObjectProvider<JwtDecoder> decoderProvider) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/.well-known/jwks.json", "/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus", "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout", "/swagger-ui/**",
                                "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .anyRequest().authenticated());
        // JWT resource-server support is enabled only when the signing/public-key configuration is present.
        // This keeps focused web tests usable without forcing a datasource or key material into the context.
        if (decoderProvider.getIfAvailable() != null) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> { }));
        }
        return http.build();
    }
}
