package com.philia.flashsale.payment.payment.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentValueObjectTests {

    @Test
    void moneyUsesExactDecimalAndCurrencyEquality() {
        assertEquals(Money.of(new BigDecimal("100.00"), "VND"),
                Money.of(new BigDecimal("100.0000"), "VND"));
        assertEquals(0, Money.of(new BigDecimal("100.00"), "VND")
                .compareTo(Money.of(new BigDecimal("100.0000"), "VND")));
    }

    @Test
    void moneyRejectsInvalidPrecisionScaleAndCurrency() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(new BigDecimal("0"), "VND"));
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(new BigDecimal("1.12345"), "VND"));
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(new BigDecimal("1"), "vnd"));
    }

    @Test
    void vndConversionRequiresExactZeroDecimalValue() {
        assertEquals(125000L, Money.of(new BigDecimal("125000.00"), "VND").toVndMinorUnits());
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(new BigDecimal("125000.50"), "VND").toVndMinorUnits());
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(new BigDecimal("10"), "USD").toVndMinorUnits());
    }

    @Test
    void typedIdentitiesAndDeadlineRejectNull() {
        UUID id = UUID.randomUUID();
        assertEquals(id, new PaymentId(id).value());
        assertEquals(id, new OrderId(id).value());
        assertThrows(IllegalArgumentException.class, () -> new PaymentId(null));
        assertThrows(IllegalArgumentException.class, () -> new OrderId(null));

        Instant deadline = Instant.parse("2026-08-17T00:00:00Z");
        PaymentDeadline paymentDeadline = new PaymentDeadline(deadline);
        assertEquals(deadline, paymentDeadline.value());
        assertEquals(true, paymentDeadline.hasPassed(deadline));
        assertThrows(IllegalArgumentException.class, () -> new PaymentDeadline(null));
    }

    @Test
    void statusAndFailureVocabularyIsExplicit() {
        assertEquals(6, PaymentStatus.values().length);
        assertEquals(7, PaymentAttemptStatus.values().length);
        assertEquals(3, FailureReason.values().length);
        assertEquals(true, PaymentAttemptStatus.UNKNOWN.unresolved());
        assertEquals(true, PaymentAttemptStatus.SUCCEEDED.terminal());
    }
}
