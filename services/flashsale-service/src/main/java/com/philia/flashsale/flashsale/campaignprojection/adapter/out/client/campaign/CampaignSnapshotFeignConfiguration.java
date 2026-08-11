package com.philia.flashsale.flashsale.campaignprojection.adapter.out.client.campaign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.flashsale.security.serviceidentity.CampaignServiceAuthorizationInterceptor;
import com.philia.flashsale.flashsale.security.serviceidentity.FlashSaleServiceTokenManager;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/** Keeps the recovery client's retry and error policies local to this Feign boundary. */
class CampaignSnapshotFeignConfiguration {

    @Bean
    ErrorDecoder campaignSnapshotErrorDecoder(ObjectMapper objectMapper) {
        return new CampaignSnapshotErrorDecoder(objectMapper);
    }

    @Bean
    Retryer campaignSnapshotRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    @ConditionalOnBean(FlashSaleServiceTokenManager.class)
    CampaignServiceAuthorizationInterceptor campaignServiceAuthorizationInterceptor(
            FlashSaleServiceTokenManager tokenManager) {
        return new CampaignServiceAuthorizationInterceptor(tokenManager);
    }
}
