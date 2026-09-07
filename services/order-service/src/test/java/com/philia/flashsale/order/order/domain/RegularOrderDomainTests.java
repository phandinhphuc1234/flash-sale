package com.philia.flashsale.order.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.order.order.domain.exception.InvalidOrderException;
import com.philia.flashsale.order.order.domain.model.Order;
import com.philia.flashsale.order.order.domain.model.OrderLine;
import com.philia.flashsale.order.order.domain.model.OrderStatus;
import com.philia.flashsale.order.order.domain.model.PurchaseSource;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.purchasesaga.domain.model.StockParticipantType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegularOrderDomainTests {
    private static final Instant ACCEPTED = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void preservesTheExistingFlashSaleParticipantContract() {
        UUID reservationId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), "FS-20300101-1", UUID.randomUUID(), reservationId,
                UUID.randomUUID(), UUID.randomUUID(), "VND", line("00000000-0000-0000-0000-000000000010", 1, "10.0000"),
                ACCEPTED, ACCEPTED.plusSeconds(300));

        assertThat(order.purchaseSource()).isEqualTo(PurchaseSource.FLASH_SALE);
        assertThat(order.stockParticipantType()).isEqualTo(StockParticipantType.FLASH_SALE_RESERVATION);
        assertThat(order.stockReferenceId()).isEqualTo(reservationId);
        assertThat(order.reservationId()).isEqualTo(reservationId);
        assertThat(order.cartId()).isNull();
        assertThat(order.cartVersion()).isNull();
    }

    @Test
    void createsBuyNowWithOneRegularStockHoldAndExactAuthoritativeTotal() {
        UUID regularHoldId = UUID.randomUUID();
        Order order = Order.regular(UUID.randomUUID(), "RN-20300101-1", UUID.randomUUID(), PurchaseSource.BUY_NOW,
                regularHoldId, UUID.randomUUID(), "VND",
                List.of(line("00000000-0000-0000-0000-000000000010", 3, "12.5000")), null, null,
                ACCEPTED, ACCEPTED.plusSeconds(300));

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.purchaseSource()).isEqualTo(PurchaseSource.BUY_NOW);
        assertThat(order.stockParticipantType()).isEqualTo(StockParticipantType.REGULAR_STOCK_HOLD);
        assertThat(order.stockReferenceId()).isEqualTo(regularHoldId);
        assertThat(order.reservationId()).isNull();
        assertThat(order.campaignId()).isNull();
        assertThat(order.total().amount()).isEqualByComparingTo("37.5000");
        assertThat(order.stockParticipantExpiresAt()).isEqualTo(ACCEPTED.plusSeconds(300));
    }

    @Test
    void createsCartOrderWithCanonicalImmutableMultipleLines() {
        OrderLine laterVariant = line("00000000-0000-0000-0000-000000000020", 1, "3.0000");
        OrderLine firstVariant = line("00000000-0000-0000-0000-000000000010", 2, "10.0000");
        UUID cartId = UUID.randomUUID();

        Order order = Order.regular(UUID.randomUUID(), "RC-20300101-1", UUID.randomUUID(), PurchaseSource.CART,
                UUID.randomUUID(), UUID.randomUUID(), "VND", List.of(laterVariant, firstVariant), cartId, 7L,
                ACCEPTED, ACCEPTED.plusSeconds(300));

        assertThat(order.lines()).containsExactly(firstVariant, laterVariant);
        assertThat(order.total().amount()).isEqualByComparingTo("23.0000");
        assertThat(order.cartId()).isEqualTo(cartId);
        assertThat(order.cartVersion()).isEqualTo(7L);
        assertThatThrownBy(() -> order.lines().add(firstVariant))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(order::line)
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("multi-line");
    }

    @Test
    void rejectsRegularOrderShapesThatWouldMakeTheirSourceAmbiguous() {
        OrderLine first = line("00000000-0000-0000-0000-000000000010", 1, "10.0000");
        OrderLine duplicateVariant = line("00000000-0000-0000-0000-000000000010", 2, "10.0000");
        OrderLine second = line("00000000-0000-0000-0000-000000000020", 1, "10.0000");

        assertThatThrownBy(() -> Order.regular(UUID.randomUUID(), "RN-invalid", UUID.randomUUID(),
                PurchaseSource.BUY_NOW, UUID.randomUUID(), UUID.randomUUID(), "VND", List.of(first, second), null,
                null, ACCEPTED, ACCEPTED.plusSeconds(300)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("exactly one line");
        assertThatThrownBy(() -> Order.regular(UUID.randomUUID(), "RC-invalid", UUID.randomUUID(),
                PurchaseSource.CART, UUID.randomUUID(), UUID.randomUUID(), "VND", List.of(first), UUID.randomUUID(),
                null, ACCEPTED, ACCEPTED.plusSeconds(300)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Cart revision");
        assertThatThrownBy(() -> Order.regular(UUID.randomUUID(), "RC-duplicate", UUID.randomUUID(),
                PurchaseSource.CART, UUID.randomUUID(), UUID.randomUUID(), "VND", List.of(first, duplicateVariant),
                UUID.randomUUID(), 0L, ACCEPTED, ACCEPTED.plusSeconds(300)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("distinct");
    }

    private static OrderLine line(String variantId, long quantity, String unitPrice) {
        return OrderLine.create(UUID.randomUUID(), UUID.fromString(variantId), quantity,
                Money.of(new BigDecimal(unitPrice)));
    }
}
