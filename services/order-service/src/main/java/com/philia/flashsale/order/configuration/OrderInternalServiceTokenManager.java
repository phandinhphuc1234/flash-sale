package com.philia.flashsale.order.configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;

/** Obtains Order's short-lived internal token without exposing it to domain/application code. */
public final class OrderInternalServiceTokenManager {

    public static final String REGISTRATION_ID = "order-internal";
    public static final String SERVICE_SUBJECT = "order-service";

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final int maxTtlSeconds;
    private final Clock clock;

    public OrderInternalServiceTokenManager(OAuth2AuthorizedClientManager authorizedClientManager, int maxTtlSeconds) {
        this(authorizedClientManager, maxTtlSeconds, Clock.systemUTC());
    }

    OrderInternalServiceTokenManager(
            OAuth2AuthorizedClientManager authorizedClientManager, int maxTtlSeconds, Clock clock) {
        this.authorizedClientManager = Objects.requireNonNull(authorizedClientManager);
        this.maxTtlSeconds = maxTtlSeconds;
        this.clock = Objects.requireNonNull(clock);
    }

    /** Returns a client-credentials Authorization header or a safe typed infrastructure failure. */
    public String authorizationHeader() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(REGISTRATION_ID)
                .principal(SERVICE_SUBJECT)
                .build();
        try {
            OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
            if (client == null || client.getAccessToken() == null
                    || client.getAccessToken().getTokenValue().isBlank()) {
                throw new OrderInternalServiceTokenException("Order internal service token is unavailable");
            }
            Instant expiresAt = client.getAccessToken().getExpiresAt();
            Instant now = clock.instant();
            if (expiresAt == null || !expiresAt.isAfter(now)
                    || Duration.between(now, expiresAt).toSeconds() > maxTtlSeconds) {
                throw new OrderInternalServiceTokenException("Order internal service token TTL is invalid");
            }
            return "Bearer " + client.getAccessToken().getTokenValue();
        } catch (OAuth2AuthorizationException exception) {
            throw new OrderInternalServiceTokenException("Order internal service token acquisition failed", exception);
        }
    }
}
