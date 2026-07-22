package com.philia.flashsale.product.application.port.out;

import java.util.UUID;

import com.philia.flashsale.product.domain.model.AdminCommandName;

public interface RecordCatalogAdminAuditPort {

    void record(
            String actorId,
            String traceId,
            AdminCommandName commandName,
            UUID targetProductId,
            String outcome,
            String errorCode,
            Long productVersion);
}
