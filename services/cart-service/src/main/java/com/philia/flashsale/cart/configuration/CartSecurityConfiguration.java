package com.philia.flashsale.cart.configuration;

import com.philia.flashsale.cart.security.CartSecurityErrorHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/** Protects shopper Cart operations and denies accidental non-Cart public endpoints. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "cart.security.jwt.enabled", havingValue = "true", matchIfMissing = true)
public class CartSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain cartApiSecurityChain(HttpSecurity http,
            @Qualifier("cartJwtDecoder") JwtDecoder decoder,
            @Qualifier("cartJwtAuthenticationConverter") Converter<Jwt, AbstractAuthenticationToken> converter,
            CartSecurityErrorHandler errorHandler) throws Exception {
        return http.securityMatcher("/api/v1/cart/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain cartDenyByDefaultSecurityChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus",
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml")
                .permitAll().anyRequest().denyAll()).build();
    }
}
