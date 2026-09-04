package com.philia.flashsale.inventory.regularhold.domain.exception;

/** Pure-domain invariant failure for the regular stock-hold aggregate. */
public class RegularStockHoldDomainException extends RuntimeException {
    public RegularStockHoldDomainException(String message) {
        super(message);
    }
}
