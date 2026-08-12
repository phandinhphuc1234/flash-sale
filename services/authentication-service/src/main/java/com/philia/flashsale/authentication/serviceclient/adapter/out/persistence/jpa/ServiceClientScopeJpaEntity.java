package com.philia.flashsale.authentication.serviceclient.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "oauth_client_scopes")
@IdClass(ServiceClientScopeId.class)
public class ServiceClientScopeJpaEntity {
    @Id @Column(name = "oauth_client_id", nullable = false) private UUID oauthClientId;
    @Id @Column(nullable = false, length = 128) private String scope;
    protected ServiceClientScopeJpaEntity() { }
    public ServiceClientScopeJpaEntity(UUID oauthClientId, String scope) { this.oauthClientId = oauthClientId; this.scope = scope; }
    public UUID getOauthClientId() { return oauthClientId; }
    public String getScope() { return scope; }
}
