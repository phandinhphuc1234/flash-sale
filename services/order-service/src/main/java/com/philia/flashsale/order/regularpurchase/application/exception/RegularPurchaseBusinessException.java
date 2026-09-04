package com.philia.flashsale.order.regularpurchase.application.exception;

import com.philia.flashsale.order.regularpurchase.application.model.ProductPurchaseQuote;
import java.util.List;
import java.util.Objects;

/** Shopper-safe deterministic checkout outcome; web mapping is deliberately deferred to T051. */
public final class RegularPurchaseBusinessException extends RuntimeException {

    public enum Reason {
        IDEMPOTENCY_KEY_REUSED,
        PURCHASE_RECOVERY_REQUIRED,
        VARIANT_NOT_FOUND,
        VARIANT_NOT_SELLABLE,
        PRICE_CHANGED,
        INSUFFICIENT_STOCK,
        INVENTORY_ITEM_NOT_FOUND
    }

    private final Reason reason;
    private final List<ProductPurchaseQuote> currentQuotes;

    public RegularPurchaseBusinessException(Reason reason) {
        this(reason, List.of());
    }

    public RegularPurchaseBusinessException(Reason reason, List<ProductPurchaseQuote> currentQuotes) {
        super(Objects.requireNonNull(reason, "reason").name());
        this.reason = reason;
        this.currentQuotes = List.copyOf(currentQuotes == null ? List.of() : currentQuotes);
    }

    public Reason reason() { return reason; }
    public List<ProductPurchaseQuote> currentQuotes() { return currentQuotes; }
}
