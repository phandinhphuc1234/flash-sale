package com.philia.flashsale.product.catalogadmin.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.domain.AdminCommandName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "AdminAuditJpaEntity")
@Table(name = "product_admin_audit_logs")
class AdminAuditJpaEntity {

    @Id
    private UUID id;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_name", nullable = false, length = 80)
    private AdminCommandName commandName;

    @Column(name = "target_product_id")
    private UUID targetProductId;

    @Column(nullable = false, length = 30)
    private String outcome;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "product_version")
    private Long productVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AdminAuditJpaEntity() {
    }

    AdminAuditJpaEntity(
            String actorId,
            String traceId,
            AdminCommandName commandName,
            UUID targetProductId,
            String outcome,
            String errorCode,
            Long productVersion,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.actorId = actorId;
        this.traceId = traceId;
        this.commandName = commandName;
        this.targetProductId = targetProductId;
        this.outcome = outcome;
        this.errorCode = errorCode;
        this.productVersion = productVersion;
        this.createdAt = createdAt;
    }
}
