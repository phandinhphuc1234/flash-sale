package com.philia.flashsale.authentication.serviceclient.adapter.in.oauth;

import com.philia.flashsale.authentication.serviceclient.application.LoadServiceClientPort;
import com.philia.flashsale.authentication.serviceclient.domain.ServiceClient;
import java.util.UUID;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Bridges the durable service-client aggregate to Spring Authorization Server. */
@Component
@ConditionalOnProperty(prefix = "flashsale.authentication", name = "runtime-enabled", havingValue = "true")
public class DurableRegisteredClientRepository implements RegisteredClientRepository {
    private final LoadServiceClientPort clients;
    public DurableRegisteredClientRepository(LoadServiceClientPort clients) { this.clients = clients; }

    @Override
    public RegisteredClient findById(String id) {
        try {
            return clients.findById(UUID.fromString(id)).filter(ServiceClient::active).map(this::toRegistered).orElse(null);
        } catch (IllegalArgumentException ignored) { return null; }
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return clients.findByClientId(clientId).filter(ServiceClient::active).map(this::toRegistered).orElse(null);
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        // The registry is provisioned from the service-owned database. Authorization Server may
        // call save while normalizing a client, but dynamic registration is intentionally absent.
    }

    private RegisteredClient toRegistered(ServiceClient client) {
        return RegisteredClient.withId(client.id().toString())
                .clientId(client.clientId())
                .clientSecret(client.clientSecretHash())
                .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scopes(scopes -> scopes.addAll(client.scopes()))
                .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(java.time.Duration.ofSeconds(client.accessTokenTtlSeconds())).build())
                .build();
    }
}
