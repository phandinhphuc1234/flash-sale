package com.philia.flashsale.authentication.serviceclient.application;

import com.philia.flashsale.authentication.serviceclient.domain.ServiceClient;
import com.philia.flashsale.authentication.serviceclient.domain.ServiceClientInactiveException;
import com.philia.flashsale.authentication.serviceclient.domain.ServiceClientNotFoundException;
import com.philia.flashsale.authentication.serviceclient.domain.ServiceClientScopeException;
import java.util.Set;

/** Applies durable identity, status, secret, grant, and least-privilege scope rules. */
public class ServiceClientAuthenticationService implements AuthenticateServiceClientUseCase {
    private final LoadServiceClientPort clients;
    private final ServiceClientSecretVerifierPort secrets;

    public ServiceClientAuthenticationService(LoadServiceClientPort clients,
            ServiceClientSecretVerifierPort secrets) {
        this.clients = clients;
        this.secrets = secrets;
    }

    @Override
    public ServiceClient authenticate(String clientId, String rawSecret, String grantType,
            Set<String> scopes) {
        ServiceClient client = clients.findByClientId(clientId)
                .orElseThrow(() -> new ServiceClientNotFoundException(clientId));
        if (!client.active()) {
            throw new ServiceClientInactiveException(clientId);
        }
        if (!secrets.matches(rawSecret, client.clientSecretHash())) {
            throw new ServiceClientNotFoundException(clientId);
        }
        if (!client.allows(grantType, scopes)) {
            throw new ServiceClientScopeException(clientId);
        }
        return client;
    }
}
