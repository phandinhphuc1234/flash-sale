package com.philia.flashsale.product.catalogadmin.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.philia.flashsale.product.catalogadmin.application.command.ChangeProductLifecycleCommand;
import com.philia.flashsale.product.catalogadmin.application.command.MaintainProductCompositionCommand;
import com.philia.flashsale.product.catalogadmin.application.exception.DuplicateVariantSkuException;
import com.philia.flashsale.product.catalogadmin.application.port.out.AdminIdempotencyPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.CheckCatalogUniquenessPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.LoadAdminProductPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.LoadExistingCategoriesPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.MaintainProductCompositionPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.ProductLifecyclePersistencePort;
import com.philia.flashsale.product.catalogadmin.application.port.out.RecordCatalogAdminAuditPort;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductDetailResult;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductVariantResult;
import com.philia.flashsale.product.catalogadmin.application.result.MutationIdempotencyDecision;
import com.philia.flashsale.product.catalogadmin.domain.AdminCommandName;
import com.philia.flashsale.product.catalogadmin.domain.CatalogAdminActor;
import com.philia.flashsale.product.catalogadmin.domain.ProductStatus;
import com.philia.flashsale.product.catalogadmin.domain.TraceId;
import com.philia.flashsale.product.catalogadmin.domain.VariantStatus;

class ProductCompositionAndLifecycleServiceTests {

    @Test
    void compositionRejectsDuplicateVariantSkuBeforePersistence() {
        UUID productId = UUID.randomUUID();
        LoadAdminProductPort loader = mock(LoadAdminProductPort.class);
        CheckCatalogUniquenessPort uniqueness = mock(CheckCatalogUniquenessPort.class);
        LoadExistingCategoriesPort categories = mock(LoadExistingCategoriesPort.class);
        MaintainProductCompositionPort persistence = mock(MaintainProductCompositionPort.class);
        when(loader.loadAdminProduct(productId)).thenReturn(java.util.Optional.of(detail(productId, ProductStatus.DRAFT, 0)));
        when(categories.existingCategoryIds(any())).thenReturn(Set.of());

        MaintainProductCompositionCommand.VariantInput first = variant(null, "SKU-1");
        MaintainProductCompositionCommand.VariantInput duplicate = variant(null, "sku-1");
        MaintainProductCompositionCommand command = new MaintainProductCompositionCommand(productId, 0,
                "Name", null, null, List.of(first, duplicate), List.of(), List.of(), actor(), trace());

        MaintainProductCompositionService service = new MaintainProductCompositionService(
                loader, uniqueness, categories, persistence);
        assertThatThrownBy(() -> service.maintain(command)).isInstanceOf(DuplicateVariantSkuException.class);
    }

    @Test
    void publishPersistsActiveLifecycleAndUsesVersionGuard() {
        UUID productId = UUID.randomUUID();
        LoadAdminProductPort loader = mock(LoadAdminProductPort.class);
        ProductLifecyclePersistencePort persistence = mock(ProductLifecyclePersistencePort.class);
        AdminIdempotencyPort idempotency = mock(AdminIdempotencyPort.class);
        RecordCatalogAdminAuditPort audit = mock(RecordCatalogAdminAuditPort.class);
        when(loader.loadAdminProduct(productId)).thenReturn(java.util.Optional.of(detail(productId, ProductStatus.DRAFT, 0)));
        when(idempotency.resolveMutation(any(), any(), any())).thenReturn(MutationIdempotencyDecision.fresh());
        when(persistence.updateLifecycle(productId, 0, ProductStatus.ACTIVE, Instant.parse("2026-01-01T00:00:00Z")))
                .thenReturn(1L);
        ProductLifecycleService service = new ProductLifecycleService(loader, persistence, idempotency, audit,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        service.publish(new ChangeProductLifecycleCommand(productId, 0, "key", actor(), trace(),
                AdminCommandName.PUBLISH_PRODUCT));

        verify(persistence).updateLifecycle(eq(productId), eq(0L), eq(ProductStatus.ACTIVE),
                eq(Instant.parse("2026-01-01T00:00:00Z")));
    }

    private static MaintainProductCompositionCommand.VariantInput variant(UUID id, String sku) {
        return new MaintainProductCompositionCommand.VariantInput(id, sku, null, "Variant",
                BigDecimal.ONE, "VND", VariantStatus.ACTIVE, 0);
    }

    private static AdminProductDetailResult detail(UUID id, ProductStatus status, long version) {
        return new AdminProductDetailResult(id, "P-1", "p-1", "Product", null, null, status, null, version,
                List.of(new AdminProductVariantResult(UUID.randomUUID(), "EXISTING", null, "Variant",
                        BigDecimal.ONE, "VND", VariantStatus.ACTIVE, 0)), List.of(), List.of());
    }

    private static CatalogAdminActor actor() { return new CatalogAdminActor("admin-1"); }

    private static TraceId trace() { return new TraceId("trace-1"); }
}
