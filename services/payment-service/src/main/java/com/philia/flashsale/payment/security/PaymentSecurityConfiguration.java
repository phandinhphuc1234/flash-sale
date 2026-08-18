package com.philia.flashsale.payment.security;

import com.philia.flashsale.payment.configuration.PaymentProperties;
import com.philia.flashsale.payment.websupport.error.PaymentAccessDeniedHandler;
import com.philia.flashsale.payment.websupport.error.PaymentAuthenticationEntryPoint;
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

/** Protects Checkout owner APIs and keeps the service deny-by-default. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = {"payment.checkout.enabled", "payment.acceptance.enabled",
        "payment.stripe.enabled"}, havingValue = "true")
public class PaymentSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain paymentApiSecurityChain(HttpSecurity http,
            @Qualifier("paymentJwtDecoder") JwtDecoder decoder,
            @Qualifier("paymentJwtAuthenticationConverter") Converter<Jwt, AbstractAuthenticationToken> converter,
            PaymentAuthenticationEntryPoint entryPoint, PaymentAccessDeniedHandler deniedHandler) throws Exception {
        return http.securityMatcher("/api/v1/payments/**")
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
    SecurityFilterChain paymentWebhookSecurityChain(HttpSecurity http, PaymentProperties properties)
            throws Exception {
        return http.securityMatcher(properties.webhookPath())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain paymentDenyByDefaultSecurityChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                .permitAll().anyRequest().denyAll()).build();
    }
}
