package com.philia.flashsale.flashsale.security.serviceidentity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;

/** Obtains and reuses the short-lived Client Credentials token for Campaign recovery. */
public final class FlashSaleServiceTokenManager {
    public static final String REGISTRATION_ID = "flashsale-campaign";
    public static final String SERVICE_SUBJECT = "flashsale-service";

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final FlashSaleServiceTokenProperties properties;
    private final Clock clock;

    public FlashSaleServiceTokenManager(OAuth2AuthorizedClientManager authorizedClientManager,
            FlashSaleServiceTokenProperties properties) {
        this(authorizedClientManager, properties, Clock.systemUTC());
    }

    FlashSaleServiceTokenManager(OAuth2AuthorizedClientManager authorizedClientManager,
            FlashSaleServiceTokenProperties properties, Clock clock) {
        this.authorizedClientManager = Objects.requireNonNull(authorizedClientManager);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Returns a cached bearer token; Spring Security renews it only after expiration. */
    public String authorizationHeader() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(REGISTRATION_ID)
                .principal(SERVICE_SUBJECT)
                .build();
        try {
            OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
            if (client == null || client.getAccessToken() == null
                    || client.getAccessToken().getTokenValue().isBlank()) {
                throw new FlashSaleServiceTokenException("Flash Sale service token is unavailable");
            }
            Instant expiresAt = client.getAccessToken().getExpiresAt();
            Instant now = clock.instant();
            if (expiresAt == null || !expiresAt.isAfter(now)
                    || Duration.between(now, expiresAt).toSeconds() > properties.maxTtlSeconds()) {
                throw new FlashSaleServiceTokenException("Flash Sale service token TTL is invalid");
            }
            return "Bearer " + client.getAccessToken().getTokenValue();
        } catch (OAuth2AuthorizationException exception) {
            throw new FlashSaleServiceTokenException(
                    "Flash Sale service token acquisition failed", exception);
        }
    }

    public String audience() {
        return properties.audience();
    }
}
