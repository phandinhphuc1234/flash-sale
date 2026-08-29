package com.philia.flashsale.product.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.philia.flashsale.product.catalog.application.port.out.LoadCatalogPort;
import com.philia.flashsale.product.catalog.application.result.VariantDisplayResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductVariantDisplayQueryServiceTests {

    @Test
    void normalizesDuplicateIdsAndPreservesFirstSeenOrder() {
        LoadCatalogPort port = mock(LoadCatalogPort.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(port.loadVariantDisplays(List.of(first, second)))
                .thenReturn(List.of(VariantDisplayResult.missing(first), VariantDisplayResult.missing(second)));

        List<VariantDisplayResult> result = new ProductVariantDisplayQueryService(port)
                .lookup(List.of(first, second, first));

        assertThat(result).extracting(VariantDisplayResult::variantId).containsExactly(first, second);
        verify(port).loadVariantDisplays(List.of(first, second));
    }

    @Test
    void emptyLookupDoesNotTouchPersistence() {
        LoadCatalogPort port = mock(LoadCatalogPort.class);

        assertThat(new ProductVariantDisplayQueryService(port).lookup(List.of())).isEmpty();

        verifyNoInteractions(port);
    }

    @Test
    void nullInputAndNullElementAreRejectedBeforePersistence() {
        LoadCatalogPort port = mock(LoadCatalogPort.class);
        ProductVariantDisplayQueryService service = new ProductVariantDisplayQueryService(port);

        assertThatThrownBy(() -> service.lookup(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lookup(java.util.Arrays.asList((UUID) null)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(port);
    }
}
