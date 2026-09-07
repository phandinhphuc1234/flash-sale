package com.philia.flashsale.product.catalogquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.philia.flashsale.product.catalogquery.application.PurchaseQuoteService;
import com.philia.flashsale.product.catalogquery.application.port.out.LoadPurchaseQuotePort;
import com.philia.flashsale.product.catalogquery.application.result.PurchaseQuoteResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure application tests for the deterministic, bounded Product quote boundary. */
class PurchaseQuoteTests {

    @Test
    void sortsUniqueVariantIdsBeforeLoadingAndPreservesTheReturnedDecisionOrder() {
        LoadPurchaseQuotePort port = mock(LoadPurchaseQuotePort.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<UUID> ordered = List.of(first, second).stream().sorted().toList();
        when(port.loadPurchaseQuotes(ordered)).thenReturn(ordered.stream().map(PurchaseQuoteResult::missing).toList());

        List<PurchaseQuoteResult> quotes = new PurchaseQuoteService(port).lookup(List.of(second, first));

        verify(port).loadPurchaseQuotes(ordered);
        assertThat(quotes).extracting(PurchaseQuoteResult::variantId).containsExactlyElementsOf(ordered);
    }

    @Test
    void rejectsEmptyDuplicateNullAndOverBoundedInputBeforePersistence() {
        LoadPurchaseQuotePort port = mock(LoadPurchaseQuotePort.class);
        PurchaseQuoteService service = new PurchaseQuoteService(port);
        UUID variantId = UUID.randomUUID();

        assertThatThrownBy(() -> service.lookup(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lookup(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lookup(List.of(variantId, variantId))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lookup(java.util.Arrays.asList((UUID) null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lookup(java.util.stream.IntStream.range(0, 21)
                .mapToObj(ignored -> UUID.randomUUID()).toList())).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(port);
    }
}
