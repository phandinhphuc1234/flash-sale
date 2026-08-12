package com.philia.flashsale.authentication.serviceclient.adapter.out.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceClientScopeJpaRepository extends JpaRepository<ServiceClientScopeJpaEntity, ServiceClientScopeId> {
    List<ServiceClientScopeJpaEntity> findByOauthClientIdOrderByScope(UUID oauthClientId);
}
