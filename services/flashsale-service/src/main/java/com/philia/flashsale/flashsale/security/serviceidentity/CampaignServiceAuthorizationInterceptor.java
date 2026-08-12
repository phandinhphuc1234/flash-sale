package com.philia.flashsale.flashsale.security.serviceidentity;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;

/**
 * Adds only the Flash Sale machine identity;
 * end-user tokens are never delegated to Campaign.
 */
public final class CampaignServiceAuthorizationInterceptor implements RequestInterceptor {
    private final FlashSaleServiceTokenManager tokenManager;

    public CampaignServiceAuthorizationInterceptor(FlashSaleServiceTokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.header(HttpHeaders.AUTHORIZATION, tokenManager.authorizationHeader());
    }
}
