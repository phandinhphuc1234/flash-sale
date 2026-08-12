package com.philia.flashsale.campaign.security.serviceidentity;

import java.util.Objects;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;

/** Obtains and reuses short-lived, scope-specific Campaign service tokens in memory. */
public final class CampaignServiceTokenManager {

    private static final String SERVICE_PRINCIPAL = "campaign-service";

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public CampaignServiceTokenManager(OAuth2AuthorizedClientManager authorizedClientManager) {
        this.authorizedClientManager = Objects.requireNonNull(authorizedClientManager);
    }

    /** Returns a bearer header for exactly the downstream capability requested by the adapter. */
    public String authorizationHeader(Capability capability) {
        Objects.requireNonNull(capability, "capability is required");
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(capability.registrationId())
                .principal(SERVICE_PRINCIPAL)
                .build();
        try {
            OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
            if (client == null || client.getAccessToken() == null
                    || client.getAccessToken().getTokenValue().isBlank()) {
                throw new CampaignServiceTokenException(
                        "Campaign service token is unavailable for " + capability.name());
            }
            return "Bearer " + client.getAccessToken().getTokenValue();
        } catch (OAuth2AuthorizationException exception) {
            throw new CampaignServiceTokenException(
                    "Campaign service token acquisition failed for " + capability.name(), exception);
        }
    }

    /** Keeps Product and Inventory registrations isolated so scopes cannot be mixed accidentally. */
    public enum Capability {
        PRODUCT_VALIDATION("campaign-product"),
        INVENTORY_ALLOCATION("campaign-inventory-allocation");

        private final String registrationId;

        Capability(String registrationId) {
            this.registrationId = registrationId;
        }

        public String registrationId() {
            return registrationId;
        }
    }
}
