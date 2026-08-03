package com.philia.flashsale.campaign.security.serviceidentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTokenManagerTests {

    @Mock
    private OAuth2AuthorizedClientManager authorizedClientManager;

    @Test
    void productCapabilityUsesOnlyTheProductRegistration() {
        when(authorizedClientManager.authorize(any())).thenReturn(authorizedClient(
                "campaign-product", "catalog.read", "product-token"));
        CampaignServiceTokenManager manager = new CampaignServiceTokenManager(authorizedClientManager);

        assertThat(manager.authorizationHeader(
                CampaignServiceTokenManager.Capability.PRODUCT_VALIDATION))
                .isEqualTo("Bearer product-token");

        ArgumentCaptor<OAuth2AuthorizeRequest> request =
                ArgumentCaptor.forClass(OAuth2AuthorizeRequest.class);
        verify(authorizedClientManager).authorize(request.capture());
        assertThat(request.getValue().getClientRegistrationId()).isEqualTo("campaign-product");
        assertThat(request.getValue().getPrincipal().getName()).isEqualTo("campaign-service");
    }

    @Test
    void inventoryCapabilityUsesOnlyTheAllocationRegistration() {
        when(authorizedClientManager.authorize(any())).thenReturn(authorizedClient(
                "campaign-inventory-allocation", "inventory.campaign.allocate", "inventory-token"));
        CampaignServiceTokenManager manager = new CampaignServiceTokenManager(authorizedClientManager);

        assertThat(manager.authorizationHeader(
                CampaignServiceTokenManager.Capability.INVENTORY_ALLOCATION))
                .isEqualTo("Bearer inventory-token");

        ArgumentCaptor<OAuth2AuthorizeRequest> request =
                ArgumentCaptor.forClass(OAuth2AuthorizeRequest.class);
        verify(authorizedClientManager).authorize(request.capture());
        assertThat(request.getValue().getClientRegistrationId())
                .isEqualTo("campaign-inventory-allocation");
    }

    private OAuth2AuthorizedClient authorizedClient(
            String registrationId, String scope, String tokenValue) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("campaign-service")
                .clientSecret("not-a-real-secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://authentication-service/oauth2/token")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                tokenValue,
                Instant.now(),
                Instant.now().plusSeconds(300),
                Set.of(scope));
        return new OAuth2AuthorizedClient(registration, "campaign-service", accessToken);
    }
}
