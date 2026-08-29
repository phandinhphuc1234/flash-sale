package com.philia.flashsale.order.security;

import com.philia.flashsale.order.websupport.error.OrderAccessDeniedHandler;
import com.philia.flashsale.order.websupport.error.OrderAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Qualifier;
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

/** Protects the public read-only Order route and denies accidental extra endpoints. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OrderSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain orderApiSecurityChain(HttpSecurity http,
            @Qualifier("orderJwtDecoder") JwtDecoder decoder,
            @Qualifier("orderJwtAuthenticationConverter") Converter<Jwt, AbstractAuthenticationToken> converter,
            OrderAuthenticationEntryPoint entryPoint, OrderAccessDeniedHandler deniedHandler) throws Exception {
        return http.securityMatcher("/api/v1/orders/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain orderDenyByDefaultSecurityChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus",
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml")
                .permitAll().anyRequest().denyAll()).build();
    }
}
