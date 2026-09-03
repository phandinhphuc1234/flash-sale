package com.philia.flashsale.cart.configuration;

import com.philia.flashsale.cart.adapter.out.client.product.ProductDisplayFeignClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/** Composition-root wiring for Cart's isolated Product client-credentials capability. */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = ProductDisplayFeignClient.class)
public class CartProductClientConfiguration {

    @Bean
    OAuth2AuthorizedClientService cartAuthorizedClientService(
            ClientRegistrationRepository registrations) {
        return new InMemoryOAuth2AuthorizedClientService(registrations);
    }

    @Bean
    OAuth2AuthorizedClientManager cartAuthorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClientService) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                registrations, authorizedClientService);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        return manager;
    }

    @Bean
    CartProductServiceTokenManager cartProductServiceTokenManager(
            OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${cart.product.token-max-ttl-seconds:300}") int maxTtlSeconds) {
        return new CartProductServiceTokenManager(authorizedClientManager, maxTtlSeconds);
    }
}
