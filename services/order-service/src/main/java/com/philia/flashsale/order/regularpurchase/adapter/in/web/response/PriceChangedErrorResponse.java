package com.philia.flashsale.order.regularpurchase.adapter.in.web.response;

import com.philia.flashsale.order.regularpurchase.application.model.ProductPurchaseQuote;
import com.philia.flashsale.common.web.FieldViolation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Explicitly approved `PRICE_CHANGED` envelope extension containing only current commercial facts. */
public record PriceChangedErrorResponse(
        boolean success,
        String errorCode,
        String message,
        List<FieldViolation> errors,
        List<CurrentPriceResponse> currentPrices,
        Instant timestamp) {

    public static PriceChangedErrorResponse from(List<ProductPurchaseQuote> quotes) {
        return new PriceChangedErrorResponse(false, "PRICE_CHANGED",
                "One or more prices changed. Review current prices and submit a new request.", null,
                quotes.stream().filter(ProductPurchaseQuote::found).map(quote -> new CurrentPriceResponse(
                        quote.variantId(), quote.unitPrice() == null ? null : quote.unitPrice().amount(),
                        quote.currency())).toList(), Instant.now());
    }

    public record CurrentPriceResponse(UUID variantId, BigDecimal currentUnitPrice, String currency) { }
}
