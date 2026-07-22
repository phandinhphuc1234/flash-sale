package com.philia.flashsale.product.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.philia.flashsale.product.domain.exception.InvalidMoneyException;

public record MoneyVnd(BigDecimal amount) {

    public static final String CURRENCY = "VND";

    // Money validation is a domain invariant because invalid prices would corrupt checkout/campaign flows later.
    public MoneyVnd {
        if (amount == null) {
            throw new InvalidMoneyException("Variant base price is required");
        }
        if (amount.signum() < 0) {
            throw new InvalidMoneyException("Variant base price must be non-negative");
        }
        if (amount.scale() > 4) {
            throw new InvalidMoneyException("Variant base price supports at most 4 decimal places");
        }
        amount = amount.setScale(4, RoundingMode.UNNECESSARY);
    }

    // Product-service currently supports one market currency; reject mixed-currency variants explicitly.
    public static MoneyVnd of(BigDecimal amount, String currency) {
        if (!CURRENCY.equals(currency)) {
            throw new InvalidMoneyException("Only VND base price is supported");
        }
        return new MoneyVnd(amount);
    }
}
