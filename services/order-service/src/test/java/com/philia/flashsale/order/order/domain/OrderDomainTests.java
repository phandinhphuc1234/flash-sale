package com.philia.flashsale.order.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.order.order.domain.exception.InvalidOrderException;
import com.philia.flashsale.order.order.domain.model.Order;
import com.philia.flashsale.order.order.domain.model.OrderLine;
import com.philia.flashsale.order.order.domain.model.OrderStatus;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderDomainTests {

    private static final Instant ACCEPTED = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void moneyUsesExactScaleFourArithmetic() {
        Money unitPrice = Money.of(new BigDecimal("12.5"));

        assertThat(unitPrice.amount()).isEqualByComparingTo("12.5000");
        assertThat(unitPrice.multiply(3).amount()).isEqualByComparingTo("37.5000");
        assertThatThrownBy(() -> Money.of(new BigDecimal("1.00001")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderLineCalculatesOneImmutableCommercialSnapshot() {
        UUID lineId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        OrderLine line = OrderLine.create(lineId, variantId, 2, Money.of(new BigDecimal("10.0000")));

        assertThat(line.id()).isEqualTo(lineId);
        assertThat(line.variantId()).isEqualTo(variantId);
        assertThat(line.lineAmount().amount()).isEqualByComparingTo("20.0000");
        assertThatThrownBy(() -> new OrderLine(lineId, variantId, 2, Money.of(new BigDecimal("10.0000")),
                Money.of(new BigDecimal("19.0000"))))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void orderStartsPendingPaymentAndPreservesTheAcceptedSnapshot() {
        UUID orderId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        OrderLine line = OrderLine.create(UUID.randomUUID(), UUID.randomUUID(), 2,
                Money.of(new BigDecimal("10.0000")));
        Order order = Order.create(orderId, "FS-20300101-" + orderId,
                purchaseRequestId, reservationId, UUID.randomUUID(), UUID.randomUUID(), "VND", line,
                ACCEPTED, ACCEPTED.plusSeconds(300));

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.subtotal().amount()).isEqualByComparingTo("20.0000");
        assertThat(order.total()).isEqualTo(order.subtotal());
        assertThat(order.line()).isSameAs(line);
        assertThatThrownBy(() -> Order.create(orderId, " ", purchaseRequestId, reservationId,
                UUID.randomUUID(), UUID.randomUUID(), "VND", line, ACCEPTED, ACCEPTED.plusSeconds(300)))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> Order.create(orderId, "FS-1", purchaseRequestId, reservationId,
                UUID.randomUUID(), UUID.randomUUID(), "vnd", line, ACCEPTED, ACCEPTED.plusSeconds(300)))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> Order.create(orderId, "FS-1", purchaseRequestId, reservationId,
                UUID.randomUUID(), UUID.randomUUID(), "VND", line, ACCEPTED, ACCEPTED))
                .isInstanceOf(InvalidOrderException.class);
    }
}
