package com.philia.flashsale.authentication.serviceclient.adapter.out.persistence.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceClientJpaRepository extends JpaRepository<ServiceClientJpaEntity, UUID> {
    Optional<ServiceClientJpaEntity> findByClientId(String clientId);
}
