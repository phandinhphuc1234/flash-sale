package com.philia.flashsale.payment.payment.domain.model;

import com.philia.flashsale.payment.payment.domain.exception.InvalidMoneyException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Exact monetary value used by Payment; floating-point arithmetic is deliberately unavailable. */
public final class Money implements Comparable<Money> {

    private static final int MAX_PRECISION = 19;
    private static final int MAX_SCALE = 4;
    private final BigDecimal amount;
    private final String currency;

    public Money(BigDecimal amount, String currency) {
        this.amount = validateAmount(amount);
        this.currency = validateCurrency(currency);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money vnd(long amount) {
        return new Money(BigDecimal.valueOf(amount), "VND");
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    /** Converts the approved VND zero-decimal amount exactly to Stripe's integer minor unit. */
    public long toVndMinorUnits() {
        if (!"VND".equals(currency)) {
            throw new InvalidMoneyException("VND conversion requires currency VND");
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new InvalidMoneyException("VND amount must have no fractional value");
        }
        try {
            return amount.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException exception) {
            throw new InvalidMoneyException("VND amount is outside the provider integer range");
        }
    }

    /** Alias used by provider adapters when converting to a provider minor-unit integer. */
    public long toStripeMinorUnits() {
        return toVndMinorUnits();
    }

    public long toProviderMinorUnits() {
        return toVndMinorUnits();
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof Money other) || !currency.equals(other.currency)) {
            return false;
        }
        return amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }

    private void requireSameCurrency(Money other) {
        if (other == null || !currency.equals(other.currency)) {
            throw new InvalidMoneyException("money currencies must match");
        }
    }

    private static BigDecimal validateAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new InvalidMoneyException("amount must be positive");
        }
        if (value.precision() > MAX_PRECISION || value.scale() > MAX_SCALE) {
            throw new InvalidMoneyException("amount must have precision <= 19 and scale <= 4");
        }
        return value;
    }

    private static String validateCurrency(String value) {
        if (value == null || !value.matches("[A-Z]{3}")) {
            throw new InvalidMoneyException("currency must be three uppercase ASCII letters");
        }
        return value;
    }
}
