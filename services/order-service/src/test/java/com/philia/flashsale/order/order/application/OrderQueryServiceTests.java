package com.philia.flashsale.order.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.order.order.application.exception.InvalidOrderQueryException;
import com.philia.flashsale.order.order.application.exception.OrderNotFoundException;
import com.philia.flashsale.order.order.application.port.out.ListOwnedOrdersPort;
import com.philia.flashsale.order.order.application.port.out.LoadOwnedOrderPort;
import com.philia.flashsale.order.order.application.query.GetOwnedOrderQuery;
import com.philia.flashsale.order.order.application.query.ListOwnedOrdersQuery;
import com.philia.flashsale.order.order.application.result.OrderDetailsResult;
import com.philia.flashsale.order.order.application.result.OrderPageResult;
import com.philia.flashsale.order.order.application.usecase.OrderQueryService;
import com.philia.flashsale.order.order.domain.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderQueryServiceTests {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();

    @Test
    void detailIsOwnerScopedAndMissingOrForeignOrdersAreNonEnumerating() {
        OrderDetailsResult expected = details(ORDER);
        LoadOwnedOrderPort port = query -> query.ownerId().equals(OWNER)
                ? Optional.of(expected) : Optional.empty();
        OrderQueryService service = new OrderQueryService(port, query -> page(), 100);

        assertThat(service.get(new GetOwnedOrderQuery(ORDER, OWNER))).isEqualTo(expected);
        assertThatThrownBy(() -> service.get(new GetOwnedOrderQuery(ORDER, UUID.randomUUID())))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void listRejectsOversizedPagesBeforeCallingThePort() {
        OrderQueryService service = new OrderQueryService(query -> Optional.empty(), query -> page(), 100);

        assertThatThrownBy(() -> service.list(new ListOwnedOrdersQuery(OWNER, 0, 101)))
                .isInstanceOf(InvalidOrderQueryException.class)
                .hasMessageContaining("100");
    }

    @Test
    void listPreservesBoundedPageResult() {
        OrderPageResult expected = page();
        ListOwnedOrdersPort port = query -> expected;
        OrderQueryService service = new OrderQueryService(query -> Optional.empty(), port, 100);

        assertThat(service.list(new ListOwnedOrdersQuery(OWNER, 2, 20))).isSameAs(expected);
    }

    private static OrderPageResult page() {
        return new OrderPageResult(java.util.List.of(), 0, 20, 0);
    }

    private static OrderDetailsResult details(UUID orderId) {
        Instant now = Instant.parse("2030-01-01T10:00:00Z");
        return new OrderDetailsResult(orderId, "FS-001", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), OrderStatus.PENDING_PAYMENT, "VND", BigDecimal.TEN, BigDecimal.TEN,
                now, now.plusSeconds(300), java.util.List.of(), now, now);
    }
}
