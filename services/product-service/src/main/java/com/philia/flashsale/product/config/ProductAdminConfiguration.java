package com.philia.flashsale.product.config;

import java.time.Clock;
import java.util.Objects;

import com.philia.flashsale.product.application.port.in.BrowseAdminCatalogUseCase;
import com.philia.flashsale.product.application.port.in.CreateProductDraftUseCase;
import com.philia.flashsale.product.application.port.in.ViewAdminProductUseCase;
import com.philia.flashsale.product.application.port.out.AdminIdempotencyPort;
import com.philia.flashsale.product.application.port.out.CheckCatalogUniquenessPort;
import com.philia.flashsale.product.application.port.out.LoadAdminProductPort;
import com.philia.flashsale.product.application.port.out.RecordCatalogAdminAuditPort;
import com.philia.flashsale.product.application.port.out.SaveAdminProductPort;
import com.philia.flashsale.product.application.usecase.AdminCatalogQueryService;
import com.philia.flashsale.product.application.usecase.AuditedCreateProductDraftUseCase;
import com.philia.flashsale.product.application.usecase.CreateProductDraftService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class ProductAdminConfiguration {

    @Bean
    // Use UTC for idempotency retention and audit timestamps so distributed services compare time consistently.
    Clock productAdminClock() {
        return Clock.systemUTC();
    }

    @Bean
    // Keep the accepted draft, replay record, and SUCCESS audit in one atomic transaction.
    CreateProductDraftUseCase createProductDraftUseCase(
            CheckCatalogUniquenessPort uniquenessPort,
            SaveAdminProductPort saveAdminProductPort,
            AdminIdempotencyPort idempotencyPort,
            RecordCatalogAdminAuditPort auditPort,
            PlatformTransactionManager transactionManager) {
        CreateProductDraftService service = new CreateProductDraftService(
                uniquenessPort,
                saveAdminProductPort,
                idempotencyPort,
                auditPort);
        TransactionTemplate mutationTransaction = new TransactionTemplate(transactionManager);
        CreateProductDraftUseCase transactionalUseCase = command -> Objects.requireNonNull(
                mutationTransaction.execute(status -> service.createDraft(command)));

        TransactionTemplate outcomeAuditTransaction = new TransactionTemplate(transactionManager);
        outcomeAuditTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        RecordCatalogAdminAuditPort durableOutcomeAudit = (
                actorId,
                traceId,
                commandName,
                targetProductId,
                outcome,
                errorCode,
                productVersion) -> outcomeAuditTransaction.executeWithoutResult(status -> auditPort.record(
                        actorId,
                        traceId,
                        commandName,
                        targetProductId,
                        outcome,
                        errorCode,
                        productVersion));

        return new AuditedCreateProductDraftUseCase(transactionalUseCase, durableOutcomeAudit);
    }

    @Bean
    // Query use cases stay simple because read-only transaction boundaries are handled by persistence adapters.
    BrowseAdminCatalogUseCase browseAdminCatalogUseCase(LoadAdminProductPort loadAdminProductPort) {
        return new AdminCatalogQueryService(loadAdminProductPort);
    }

    @Bean
    ViewAdminProductUseCase viewAdminProductUseCase(LoadAdminProductPort loadAdminProductPort) {
        return new AdminCatalogQueryService(loadAdminProductPort);
    }
}
