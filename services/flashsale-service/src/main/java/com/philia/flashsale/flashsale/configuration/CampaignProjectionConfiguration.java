package com.philia.flashsale.flashsale.configuration;

import com.philia.flashsale.flashsale.campaignprojection.adapter.out.redis.CampaignProjectionRedisAdapter;
import com.philia.flashsale.flashsale.campaignprojection.adapter.out.client.campaign.CampaignSnapshotClientMapper;
import com.philia.flashsale.flashsale.campaignprojection.application.port.out.LoadCampaignSnapshotPort;
import com.philia.flashsale.flashsale.campaignprojection.application.port.out.QueueCampaignRecoveryPort;
import com.philia.flashsale.flashsale.campaignprojection.application.port.out.StoreCampaignProjectionPort;
import com.philia.flashsale.flashsale.campaignprojection.application.usecase.CampaignProjectionRecoveryService;
import com.philia.flashsale.flashsale.campaignprojection.application.usecase.CampaignProjectionService;
import com.philia.flashsale.flashsale.security.serviceidentity.FlashSaleServiceTokenManager;
import com.philia.flashsale.flashsale.security.serviceidentity.FlashSaleServiceTokenProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/** Composition-root wiring for Campaign projection, recovery, and service identity. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FlashSaleServiceTokenProperties.class)
@ConditionalOnBean(StringRedisTemplate.class)
public class CampaignProjectionConfiguration {

    @Bean
    CampaignProjectionRedisAdapter campaignProjectionRedisAdapter(StringRedisTemplate redis) {
        return new CampaignProjectionRedisAdapter(redis);
    }

    @Bean
    CampaignProjectionService campaignProjectionService(
            StoreCampaignProjectionPort store, QueueCampaignRecoveryPort recoveryQueue) {
        return new CampaignProjectionService(store, recoveryQueue);
    }

    @Bean
    CampaignProjectionRecoveryService campaignProjectionRecoveryService(
            LoadCampaignSnapshotPort snapshotLoader,
            StoreCampaignProjectionPort store,
            QueueCampaignRecoveryPort recoveryQueue) {
        return new CampaignProjectionRecoveryService(snapshotLoader, store, recoveryQueue);
    }

    @Bean
    OAuth2AuthorizedClientService flashSaleAuthorizedClientService(
            ClientRegistrationRepository registrations) {
        return new InMemoryOAuth2AuthorizedClientService(registrations);
    }

    @Bean
    AuthorizedClientServiceOAuth2AuthorizedClientManager flashSaleAuthorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClientService) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                registrations, authorizedClientService);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials().build());
        return manager;
    }

    @Bean
    FlashSaleServiceTokenManager flashSaleServiceTokenManager(
            AuthorizedClientServiceOAuth2AuthorizedClientManager manager,
            FlashSaleServiceTokenProperties properties) {
        return new FlashSaleServiceTokenManager(manager, properties);
    }

    @Bean
    CampaignSnapshotClientMapper campaignSnapshotClientMapper() {
        return new CampaignSnapshotClientMapper();
    }
}
