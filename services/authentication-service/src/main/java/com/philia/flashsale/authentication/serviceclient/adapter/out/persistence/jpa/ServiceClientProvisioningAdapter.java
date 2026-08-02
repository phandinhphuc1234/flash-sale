package com.philia.flashsale.authentication.serviceclient.adapter.out.persistence.jpa;

import com.philia.flashsale.authentication.serviceclient.application.ProvisionServiceClientPort;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "flashsale.authentication", name = "runtime-enabled", havingValue = "true")
public class ServiceClientProvisioningAdapter implements ProvisionServiceClientPort {
    private final ServiceClientJpaRepository clients;
    private final ServiceClientScopeJpaRepository scopes;
    private final PasswordEncoder encoder;
    public ServiceClientProvisioningAdapter(ServiceClientJpaRepository clients, ServiceClientScopeJpaRepository scopes,
            PasswordEncoder encoder) { this.clients = clients; this.scopes = scopes; this.encoder = encoder; }

    @Override
    @Transactional
    public void provisionIfAbsent(String clientId, String rawSecret, List<String> allowedScopes) {
        if (clients.findByClientId(clientId).isPresent()) return;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID id = UUID.randomUUID();
        clients.save(new ServiceClientJpaEntity(id, clientId, encoder.encode(rawSecret),
                "client_credentials", "ACTIVE", 300, now, now));
        allowedScopes.stream().distinct().map(scope -> new ServiceClientScopeJpaEntity(id, scope))
                .forEach(scopes::save);
    }
}
