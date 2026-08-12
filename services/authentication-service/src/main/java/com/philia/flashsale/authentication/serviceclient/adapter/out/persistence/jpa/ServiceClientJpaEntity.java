package com.philia.flashsale.authentication.serviceclient.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "oauth_clients")
public class ServiceClientJpaEntity {
    @Id private UUID id;
    @Column(name = "client_id", nullable = false, unique = true, length = 100) private String clientId;
    @Column(name = "client_secret_hash", nullable = false, length = 255) private String clientSecretHash;
    @Column(name = "grant_type", nullable = false, length = 64) private String grantType;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "access_token_ttl_seconds", nullable = false) private int accessTokenTtlSeconds;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    protected ServiceClientJpaEntity() { }
    public ServiceClientJpaEntity(UUID id, String clientId, String clientSecretHash, String grantType,
            String status, int accessTokenTtlSeconds, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id; this.clientId = clientId; this.clientSecretHash = clientSecretHash;
        this.grantType = grantType; this.status = status; this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.createdAt = createdAt; this.updatedAt = updatedAt;
    }
    public UUID getId() { return id; }
    public String getClientId() { return clientId; }
    public String getClientSecretHash() { return clientSecretHash; }
    public String getGrantType() { return grantType; }
    public String getStatus() { return status; }
    public int getAccessTokenTtlSeconds() { return accessTokenTtlSeconds; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
