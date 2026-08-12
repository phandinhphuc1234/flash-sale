package com.philia.flashsale.product.configuration;

import java.time.Clock;
import java.util.Objects;

import com.philia.flashsale.product.catalogadmin.application.port.in.BrowseAdminCatalogUseCase;
import com.philia.flashsale.product.catalogadmin.application.port.in.CreateProductDraftUseCase;
import com.philia.flashsale.product.catalogadmin.application.port.in.ViewAdminProductUseCase;
import com.philia.flashsale.product.catalogadmin.application.port.in.MaintainProductCompositionUseCase;
import com.philia.flashsale.product.catalogadmin.application.port.in.PublishProductUseCase;
import com.philia.flashsale.product.catalogadmin.application.port.in.DeactivateProductUseCase;
import com.philia.flashsale.product.catalogadmin.application.port.in.ReactivateProductUseCase;
import com.philia.flashsale.product.catalogadmin.application.port.in.ArchiveProductUseCase;
import com.philia.flashsale.product.catalogadmin.application.port.out.AdminIdempotencyPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.CheckCatalogUniquenessPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.LoadAdminProductPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.LoadExistingCategoriesPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.RecordCatalogAdminAuditPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.SaveAdminProductPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.MaintainProductCompositionPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.ProductLifecyclePersistencePort;
import com.philia.flashsale.product.catalogadmin.application.usecase.AdminCatalogQueryService;
import com.philia.flashsale.product.catalogadmin.application.usecase.AuditedCreateProductDraftUseCase;
import com.philia.flashsale.product.catalogadmin.application.usecase.CreateProductDraftService;
import com.philia.flashsale.product.catalogadmin.application.service.MaintainProductCompositionService;
import com.philia.flashsale.product.catalogadmin.application.service.ProductLifecycleService;
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

    @Bean
    MaintainProductCompositionUseCase maintainProductCompositionUseCase(
            LoadAdminProductPort loader, CheckCatalogUniquenessPort uniqueness,
            LoadExistingCategoriesPort categories,
            MaintainProductCompositionPort persistence, PlatformTransactionManager transactionManager) {
        MaintainProductCompositionService service = new MaintainProductCompositionService(loader, uniqueness, categories, persistence);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> service.maintain(command)));
    }

    @Bean
    ProductLifecycleService productLifecycleService(LoadAdminProductPort loader,
            ProductLifecyclePersistencePort persistence, AdminIdempotencyPort idempotency,
            RecordCatalogAdminAuditPort audit, Clock productAdminClock) {
        return new ProductLifecycleService(loader, persistence, idempotency, audit, productAdminClock);
    }

    @Bean
    PublishProductUseCase publishProductUseCase(ProductLifecycleService service, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> service.publish(command)));
    }

    @Bean
    DeactivateProductUseCase deactivateProductUseCase(ProductLifecycleService service, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> service.deactivate(command)));
    }

    @Bean
    ReactivateProductUseCase reactivateProductUseCase(ProductLifecycleService service, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> service.reactivate(command)));
    }

    @Bean
    ArchiveProductUseCase archiveProductUseCase(ProductLifecycleService service, PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> service.archive(command)));
    }
}
