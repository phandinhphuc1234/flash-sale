package com.philia.flashsale.order.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import feign.RequestInterceptor;

/** Composition-root wiring for Order's one least-privilege client-credentials token. */
@Configuration(proxyBeanMethods = false)
public class OrderInternalClientConfiguration {

    @Bean
    OAuth2AuthorizedClientService orderAuthorizedClientService(ClientRegistrationRepository registrations) {
        return new InMemoryOAuth2AuthorizedClientService(registrations);
    }

    @Bean
    OAuth2AuthorizedClientManager orderAuthorizedClientManager(
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
    OrderInternalServiceTokenManager orderInternalServiceTokenManager(
            OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${order.regular-purchase.internal-clients.token-max-ttl-seconds:300}") int maxTtlSeconds) {
        return new OrderInternalServiceTokenManager(authorizedClientManager, maxTtlSeconds);
    }

    @Bean
    RequestInterceptor orderInternalAuthorizationRequestInterceptor(OrderInternalServiceTokenManager tokenManager) {
        return new OrderInternalAuthorizationRequestInterceptor(tokenManager);
    }
}
