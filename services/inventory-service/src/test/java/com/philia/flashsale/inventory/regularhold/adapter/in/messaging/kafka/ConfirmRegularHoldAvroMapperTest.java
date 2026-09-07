package com.philia.flashsale.inventory.regularhold.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.contract.regularhold.command.v1.ConfirmRegularStockHoldDataV1;
import com.philia.flashsale.contract.regularhold.command.v1.ConfirmRegularStockHoldV1;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class ConfirmRegularHoldAvroMapperTest {
    private final ConfirmRegularHoldAvroMapper mapper = new ConfirmRegularHoldAvroMapper();

    @Test
    void mapsOnlyTheExactOrderConfirmEnvelopeAndPreservesSourcePosition() {
        UUID sagaId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-04T13:31:00Z");
        ConfirmRegularStockHoldV1 value = new ConfirmRegularStockHoldV1(eventId, "ConfirmRegularStockHold", 1,
                "order-service", "PURCHASE_SAGA", sagaId, 4L, purchaseRequestId, UUID.randomUUID(), now,
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", null,
                new ConfirmRegularStockHoldDataV1(sagaId, orderId, purchaseRequestId, holdId, paymentId, now));

        var message = mapper.map(new ConsumerRecord<>("flashsale.inventory.regular-hold.commands.v1", 3, 17L,
                orderId.toString(), value));

        assertThat(message.commandId()).isEqualTo(eventId);
        assertThat(message.command().holdId()).isEqualTo(holdId);
        assertThat(message.command().orderId()).isEqualTo(orderId);
        assertThat(message.sourceTopic()).isEqualTo("flashsale.inventory.regular-hold.commands.v1");
        assertThat(message.sourcePartition()).isEqualTo(3);
        assertThat(message.sourceOffset()).isEqualTo(17L);
        assertThat(message.payloadFingerprint()).matches("[0-9a-f]{64}");
    }

    @Test
    void rejectsAnEnvelopeWhoseKafkaKeyDoesNotEqualOrderId() {
        UUID sagaId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-04T13:31:00Z");
        ConfirmRegularStockHoldV1 value = new ConfirmRegularStockHoldV1(UUID.randomUUID(),
                "ConfirmRegularStockHold", 1, "order-service", "PURCHASE_SAGA", sagaId, 1L,
                purchaseRequestId, UUID.randomUUID(), now, null, null,
                new ConfirmRegularStockHoldDataV1(sagaId, orderId, purchaseRequestId, UUID.randomUUID(),
                        UUID.randomUUID(), now));

        assertThatThrownBy(() -> mapper.map(new ConsumerRecord<>("topic", 0, 0L, UUID.randomUUID().toString(), value)))
                .isInstanceOf(RegularHoldCommandRecordException.class)
                .hasMessageContaining("correlation or key");
    }
}
