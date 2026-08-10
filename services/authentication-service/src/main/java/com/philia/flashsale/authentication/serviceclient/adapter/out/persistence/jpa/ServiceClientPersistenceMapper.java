package com.philia.flashsale.authentication.serviceclient.adapter.out.persistence.jpa;

import com.philia.flashsale.authentication.serviceclient.domain.ServiceClient;
import com.philia.flashsale.authentication.serviceclient.domain.ServiceClientGrantType;
import com.philia.flashsale.authentication.serviceclient.domain.ServiceClientStatus;
import java.util.Locale;
import java.util.Set;

public final class ServiceClientPersistenceMapper {
    private ServiceClientPersistenceMapper() { }
    public static ServiceClient toDomain(ServiceClientJpaEntity entity, Set<String> scopes) {
        return new ServiceClient(entity.getId(), entity.getClientId(), entity.getClientSecretHash(),
                ServiceClientGrantType.valueOf(entity.getGrantType().toUpperCase(Locale.ROOT)),
                ServiceClientStatus.valueOf(entity.getStatus().toUpperCase(Locale.ROOT)),
                entity.getAccessTokenTtlSeconds(), scopes, entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
