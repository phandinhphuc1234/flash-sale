package com.philia.flashsale.cart.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotDataV1;
import com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotItemV1;
import com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotV1;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class ReconcilePurchasedCartSnapshotAvroMapperTests {
    private static final String TOPIC = "flashsale.cart.checkout.commands.v1";
    private final ReconcilePurchasedCartSnapshotAvroMapper mapper = new ReconcilePurchasedCartSnapshotAvroMapper();

    @Test
    void mapsOrderOwnedIdentityAndKafkaPosition() {
        var event = event();
        var mapped = mapper.map(record(event));

        assertThat(mapped.commandId()).isEqualTo(event.getEventId());
        assertThat(mapped.orderId()).isEqualTo(event.getData().getOrderId());
        assertThat(mapped.cartId()).isEqualTo(event.getData().getCartId());
        assertThat(mapped.items()).hasSize(2);
        assertThat(mapped.sourceTopic()).isEqualTo(TOPIC);
        assertThat(mapped.sourcePartition()).isZero();
        assertThat(mapped.sourceOffset()).isEqualTo(42L);
    }

    @Test
    void rejectsWrongEnvelopeOrMismatchedAggregate() {
        var event = event();
        assertThatThrownBy(() -> mapper.map(record(
                ReconcilePurchasedCartSnapshotV1.newBuilder(event).setProducer("cart-service").build())))
                .isInstanceOf(CartReconciliationRecordException.class);
        var mismatch = ReconcilePurchasedCartSnapshotDataV1.newBuilder(event.getData())
                .setOrderId(UUID.randomUUID()).build();
        assertThatThrownBy(() -> mapper.map(record(
                ReconcilePurchasedCartSnapshotV1.newBuilder(event).setData(mismatch).build())))
                .isInstanceOf(CartReconciliationRecordException.class);
    }

    private ConsumerRecord<String, ReconcilePurchasedCartSnapshotV1> record(
            ReconcilePurchasedCartSnapshotV1 event) {
        return new ConsumerRecord<>(TOPIC, 0, 42L, event.getAggregateId().toString(), event);
    }

    private ReconcilePurchasedCartSnapshotV1 event() {
        UUID orderId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-05T00:00:00Z");
        var data = new ReconcilePurchasedCartSnapshotDataV1(orderId, requestId, UUID.randomUUID(),
                UUID.randomUUID(), 2L, now, List.of(
                        new ReconcilePurchasedCartSnapshotItemV1(UUID.randomUUID(), 2L, 1L),
                        new ReconcilePurchasedCartSnapshotItemV1(UUID.randomUUID(), 1L, 2L)));
        return new ReconcilePurchasedCartSnapshotV1(UUID.randomUUID(), "ReconcilePurchasedCartSnapshot", 1,
                "order-service", "ORDER", orderId, 1L, requestId, UUID.randomUUID(), now,
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", null, data);
    }
}
