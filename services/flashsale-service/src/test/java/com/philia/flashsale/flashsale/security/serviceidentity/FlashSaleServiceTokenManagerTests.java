package com.philia.flashsale.flashsale.security.serviceidentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

class FlashSaleServiceTokenManagerTests {
    private static final Instant NOW = Instant.parse("2030-08-01T10:00:00Z");
    private static final FlashSaleServiceTokenProperties PROPERTIES =
            new FlashSaleServiceTokenProperties("flash-sale-internal-api", 300);

    @Test
    void requestsTheFlashSalePrincipalAndReturnsTheCachedClientCredentialsToken() {
        OAuth2AuthorizedClientManager manager = org.mockito.Mockito.mock(OAuth2AuthorizedClientManager.class);
        OAuth2AuthorizedClient client = authorizedClient(NOW.plusSeconds(120));
        when(manager.authorize(any())).thenReturn(client);
        FlashSaleServiceTokenManager tokenManager = new FlashSaleServiceTokenManager(
                manager, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(tokenManager.authorizationHeader()).isEqualTo("Bearer access-token");
        assertThat(tokenManager.audience()).isEqualTo("flash-sale-internal-api");

        ArgumentCaptor<OAuth2AuthorizeRequest> request = ArgumentCaptor.forClass(OAuth2AuthorizeRequest.class);
        verify(manager).authorize(request.capture());
        assertThat(request.getValue().getClientRegistrationId())
                .isEqualTo(FlashSaleServiceTokenManager.REGISTRATION_ID);
        assertThat(request.getValue().getPrincipal().getName())
                .isEqualTo(FlashSaleServiceTokenManager.SERVICE_SUBJECT);
    }

    @Test
    void rejectsTokensLongerThanTheApprovedMaximumTtl() {
        OAuth2AuthorizedClientManager manager = org.mockito.Mockito.mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any())).thenReturn(authorizedClient(NOW.plusSeconds(301)));
        FlashSaleServiceTokenManager tokenManager = new FlashSaleServiceTokenManager(
                manager, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(tokenManager::authorizationHeader)
                .isInstanceOf(FlashSaleServiceTokenException.class);
    }

    private OAuth2AuthorizedClient authorizedClient(Instant expiresAt) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("flashsale-campaign")
                .clientId("flashsale-service")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://authentication/oauth2/token")
                .build();
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "access-token", NOW, expiresAt);
        return new OAuth2AuthorizedClient(registration, "flashsale-service", token);
    }
}
