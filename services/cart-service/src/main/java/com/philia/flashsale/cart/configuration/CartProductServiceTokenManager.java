package com.philia.flashsale.cart.configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;

/** Obtains and reuses Cart's short-lived client-credentials token for Product. */
public final class CartProductServiceTokenManager {

    public static final String REGISTRATION_ID = "cart-product";
    public static final String SERVICE_SUBJECT = "cart-service";

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final int maxTtlSeconds;
    private final Clock clock;

    public CartProductServiceTokenManager(
            OAuth2AuthorizedClientManager authorizedClientManager, int maxTtlSeconds) {
        this(authorizedClientManager, maxTtlSeconds, Clock.systemUTC());
    }

    CartProductServiceTokenManager(
            OAuth2AuthorizedClientManager authorizedClientManager, int maxTtlSeconds, Clock clock) {
        this.authorizedClientManager = Objects.requireNonNull(authorizedClientManager);
        this.maxTtlSeconds = maxTtlSeconds;
        this.clock = Objects.requireNonNull(clock);
    }

    /** Returns the cached token; Spring renews it after expiration. */
    public String authorizationHeader() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(REGISTRATION_ID)
                .principal(SERVICE_SUBJECT)
                .build();
        try {
            OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
            if (client == null || client.getAccessToken() == null
                    || client.getAccessToken().getTokenValue().isBlank()) {
                throw new CartProductServiceTokenException("Cart Product service token is unavailable");
            }
            Instant expiresAt = client.getAccessToken().getExpiresAt();
            Instant now = clock.instant();
            if (expiresAt == null || !expiresAt.isAfter(now)
                    || Duration.between(now, expiresAt).toSeconds() > maxTtlSeconds) {
                throw new CartProductServiceTokenException("Cart Product service token TTL is invalid");
            }
            return "Bearer " + client.getAccessToken().getTokenValue();
        } catch (OAuth2AuthorizationException exception) {
            throw new CartProductServiceTokenException(
                    "Cart Product service token acquisition failed", exception);
        }
    }
}
