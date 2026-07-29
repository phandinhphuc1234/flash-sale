package com.philia.flashsale.product.catalogadmin.application.port.out;

import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.domain.AdminCommandName;

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
