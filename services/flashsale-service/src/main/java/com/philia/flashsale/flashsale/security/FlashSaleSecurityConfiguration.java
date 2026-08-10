package com.philia.flashsale.flashsale.security;

import com.philia.flashsale.flashsale.websupport.error.FlashSaleAccessDeniedHandler;
import com.philia.flashsale.flashsale.websupport.error.FlashSaleAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/** Protects the public reservation boundary and denies unrecognised application paths. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class FlashSaleSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain flashSaleApiSecurityChain(
            HttpSecurity http,
            @Qualifier("flashSaleJwtDecoder") JwtDecoder decoder,
            @Qualifier("flashSaleJwtAuthenticationConverter") Converter<Jwt, AbstractAuthenticationToken> converter,
            FlashSaleAuthenticationEntryPoint entryPoint,
            FlashSaleAccessDeniedHandler deniedHandler) throws Exception {
        return http
                .securityMatcher("/api/v1/flash-sales/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain flashSaleDenyByDefaultSecurityChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
