package com.philia.flashsale.authentication.serviceclient.application;

import com.philia.flashsale.authentication.serviceclient.domain.ServiceClient;
import java.util.Optional;
import java.util.UUID;

public interface LoadServiceClientPort {
    Optional<ServiceClient> findByClientId(String clientId);
    Optional<ServiceClient> findById(UUID id);
}
