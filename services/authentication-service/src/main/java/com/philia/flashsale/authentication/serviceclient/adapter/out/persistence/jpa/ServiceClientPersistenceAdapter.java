package com.philia.flashsale.authentication.serviceclient.adapter.out.persistence.jpa;

import com.philia.flashsale.authentication.serviceclient.application.LoadServiceClientPort;
import com.philia.flashsale.authentication.serviceclient.domain.ServiceClient;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "flashsale.authentication", name = "runtime-enabled", havingValue = "true")
public class ServiceClientPersistenceAdapter implements LoadServiceClientPort {
    private final ServiceClientJpaRepository clients;
    private final ServiceClientScopeJpaRepository scopes;
    public ServiceClientPersistenceAdapter(ServiceClientJpaRepository clients, ServiceClientScopeJpaRepository scopes) { this.clients = clients; this.scopes = scopes; }
    @Override public Optional<ServiceClient> findByClientId(String clientId) { return clients.findByClientId(clientId).map(this::map); }
    @Override public Optional<ServiceClient> findById(UUID id) { return clients.findById(id).map(this::map); }
    private ServiceClient map(ServiceClientJpaEntity entity) {
        return ServiceClientPersistenceMapper.toDomain(entity, scopes.findByOauthClientIdOrderByScope(entity.getId()).stream().map(ServiceClientScopeJpaEntity::getScope).collect(Collectors.toUnmodifiableSet()));
    }
}
