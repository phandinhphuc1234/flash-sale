package com.philia.flashsale.product.catalogquery.application;

import com.philia.flashsale.product.catalogquery.application.port.in.LookupPurchaseQuotesUseCase;
import com.philia.flashsale.product.catalogquery.application.port.out.LoadPurchaseQuotePort;
import com.philia.flashsale.product.catalogquery.application.result.PurchaseQuoteResult;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Normalizes a bounded Order-only decision query before delegating to Product persistence.
 *
 * <p>The input is deliberately rejected rather than deduplicated: duplicate variants would make
 * the caller's purchase intent ambiguous and violate the published internal contract.</p>
 */
public final class PurchaseQuoteService implements LookupPurchaseQuotesUseCase {

    public static final int MAXIMUM_VARIANT_LINES = 20;

    private final LoadPurchaseQuotePort loadPurchaseQuotePort;

    public PurchaseQuoteService(LoadPurchaseQuotePort loadPurchaseQuotePort) {
        this.loadPurchaseQuotePort = Objects.requireNonNull(loadPurchaseQuotePort);
    }

    @Override
    public List<PurchaseQuoteResult> lookup(List<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            throw new IllegalArgumentException("variantIds must not be empty");
        }
        if (variantIds.size() > MAXIMUM_VARIANT_LINES) {
            throw new IllegalArgumentException("variantIds must contain at most " + MAXIMUM_VARIANT_LINES + " values");
        }
        if (variantIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("variantIds cannot contain null");
        }
        if (variantIds.stream().distinct().count() != variantIds.size()) {
            throw new IllegalArgumentException("variantIds cannot contain duplicates");
        }

        List<UUID> orderedVariantIds = variantIds.stream().sorted().toList();
        return List.copyOf(loadPurchaseQuotePort.loadPurchaseQuotes(orderedVariantIds));
    }
}
