package com.philia.flashsale.authentication.serviceclient.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Durable machine identity used for internal OAuth2 client-credentials tokens. */
public record ServiceClient(
        UUID id,
        String clientId,
        String clientSecretHash,
        ServiceClientGrantType grantType,
        ServiceClientStatus status,
        int accessTokenTtlSeconds,
        Set<String> scopes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public ServiceClient {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(clientSecretHash, "clientSecretHash");
        Objects.requireNonNull(grantType, "grantType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(scopes, "scopes");
        if (clientId.isBlank() || clientSecretHash.isBlank()) {
            throw new IllegalArgumentException("service client identity and secret hash are required");
        }
        if (accessTokenTtlSeconds < 1 || accessTokenTtlSeconds > 300) {
            throw new IllegalArgumentException("service token TTL must be between 1 and 300 seconds");
        }
        scopes = Set.copyOf(scopes);
    }

    public boolean active() {
        return status == ServiceClientStatus.ACTIVE;
    }

    public boolean allows(String requestedGrantType, Set<String> requestedScopes) {
        if (!active() || !ServiceClientGrantType.CLIENT_CREDENTIALS.name().equals(requestedGrantType)) {
            return false;
        }
        return scopes.containsAll(requestedScopes);
    }
}
