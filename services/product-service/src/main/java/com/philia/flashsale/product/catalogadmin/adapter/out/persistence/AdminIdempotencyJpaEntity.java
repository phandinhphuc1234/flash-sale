package com.philia.flashsale.product.catalogadmin.adapter.out.persistence;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.domain.AdminCommandName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "AdminIdempotencyJpaEntity")
@Table(name = "product_admin_idempotency_keys")
class AdminIdempotencyJpaEntity {

    @Id
    private UUID id;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_name", nullable = false, length = 80)
    private AdminCommandName commandName;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Column(name = "target_product_id")
    private UUID targetProductId;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AdminIdempotencyJpaEntity() {
    }

    AdminIdempotencyJpaEntity(
            String actorId,
            String idempotencyKey,
            AdminCommandName commandName,
            String requestHash,
            UUID targetProductId,
            int httpStatus,
            Map<String, Object> responseBody,
            Instant now,
            Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.actorId = actorId;
        this.idempotencyKey = idempotencyKey;
        this.commandName = commandName;
        this.requestHash = requestHash;
        this.targetProductId = targetProductId;
        this.httpStatus = httpStatus;
        this.responseBody = Map.copyOf(responseBody);
        this.createdAt = now;
        this.completedAt = now;
        this.expiresAt = expiresAt;
    }

    String requestHash() {
        return requestHash;
    }

    UUID targetProductId() { return targetProductId; }
    int httpStatus() { return httpStatus; }

    String status() {
        Object value = responseBody.get("status");
        return value == null ? null : value.toString();
    }

    long version() {
        Object value = responseBody.get("version");
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    Instant expiresAt() {
        return expiresAt;
    }

    Map<String, Object> responseBody() {
        return responseBody;
    }
}
