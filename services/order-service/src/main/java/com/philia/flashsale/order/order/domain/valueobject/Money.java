package com.philia.flashsale.order.order.domain.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Exact positive scale-four monetary value used by the Order aggregate. */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final int SCALE = 4;

    public Money {
        Objects.requireNonNull(amount, "amount");
        try {
            amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("money must be representable at scale 4", exception);
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("money must be positive");
        }
        if (amount.precision() > 19) {
            throw new IllegalArgumentException("money exceeds NUMERIC(19,4)");
        }
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public Money multiply(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "other");
        return new Money(amount.add(other.amount));
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }
}
