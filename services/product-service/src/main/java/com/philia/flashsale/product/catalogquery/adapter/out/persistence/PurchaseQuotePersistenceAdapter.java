package com.philia.flashsale.product.catalogquery.adapter.out.persistence;

import com.philia.flashsale.product.catalogquery.application.port.out.LoadPurchaseQuotePort;
import com.philia.flashsale.product.catalogquery.application.result.PurchaseQuoteResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter that returns one Product-owned quote or a missing result for every requested ID. */
@Repository
@Transactional(readOnly = true)
class PurchaseQuotePersistenceAdapter implements LoadPurchaseQuotePort {

    private final PurchaseQuoteJpaRepository repository;

    PurchaseQuotePersistenceAdapter(PurchaseQuoteJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<PurchaseQuoteResult> loadPurchaseQuotes(List<UUID> orderedVariantIds) {
        Map<UUID, PurchaseQuoteResult> quotesByVariantId = repository
                .findPurchaseQuotesByVariantIds(orderedVariantIds)
                .stream()
                .collect(Collectors.toMap(
                        PurchaseQuoteJpaRepository.PurchaseQuoteRow::getVariantId,
                        this::toResult,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        return orderedVariantIds.stream()
                .map(variantId -> quotesByVariantId.getOrDefault(variantId, PurchaseQuoteResult.missing(variantId)))
                .toList();
    }

    private PurchaseQuoteResult toResult(PurchaseQuoteJpaRepository.PurchaseQuoteRow row) {
        return new PurchaseQuoteResult(
                row.getVariantId(),
                true,
                row.isSellable(),
                row.getUnavailableReason(),
                row.getProductId(),
                row.getSku(),
                row.getProductName(),
                row.getVariantName(),
                row.getUnitPrice(),
                row.getCurrency(),
                row.getCatalogVersion());
    }
}
