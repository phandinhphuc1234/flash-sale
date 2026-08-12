package com.philia.flashsale.campaign.configuration;

import com.philia.flashsale.campaign.campaign.adapter.out.client.inventory.InventoryFeignClient;
import com.philia.flashsale.campaign.campaign.adapter.out.client.product.ProductFeignClient;
import com.philia.flashsale.campaign.security.serviceidentity.CampaignServiceTokenManager;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/** Wires Campaign's background-safe Client Credentials manager and intended Feign clients. */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = {ProductFeignClient.class, InventoryFeignClient.class})
public class CampaignOAuth2ClientConfiguration {

    @Bean
    OAuth2AuthorizedClientService campaignAuthorizedClientService(
            ClientRegistrationRepository registrations) {
        return new InMemoryOAuth2AuthorizedClientService(registrations);
    }

    /** Uses Spring Security's in-memory cache and renews expired Client Credentials tokens. */
    @Bean
    AuthorizedClientServiceOAuth2AuthorizedClientManager campaignAuthorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClientService) {
        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        registrations, authorizedClientService);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    @Bean
    CampaignServiceTokenManager campaignServiceTokenManager(
            AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager) {
        return new CampaignServiceTokenManager(authorizedClientManager);
    }
}
