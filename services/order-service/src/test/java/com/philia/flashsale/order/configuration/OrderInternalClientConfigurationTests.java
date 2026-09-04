package com.philia.flashsale.order.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import feign.RequestTemplate;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

/** Keeps the OAuth registration/subject constants aligned with Authentication's fixed Order client. */
class OrderInternalClientConfigurationTests {

    @Test
    void usesTheApprovedOrderOnlyMachineIdentity() {
        assertThat(OrderInternalServiceTokenManager.REGISTRATION_ID).isEqualTo("order-internal");
        assertThat(OrderInternalServiceTokenManager.SERVICE_SUBJECT).isEqualTo("order-service");
    }

    @Test
    void propagatesOnlyTheShortLivedMachineBearerHeader() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
        Instant now = Instant.now();
        when(client.getAccessToken()).thenReturn(new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "machine-token", now, now.plusSeconds(120)));
        when(manager.authorize(org.mockito.ArgumentMatchers.any())).thenReturn(client);

        RequestTemplate template = new RequestTemplate();
        new OrderInternalAuthorizationRequestInterceptor(
                new OrderInternalServiceTokenManager(manager, 300)).apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer machine-token");
    }
}
