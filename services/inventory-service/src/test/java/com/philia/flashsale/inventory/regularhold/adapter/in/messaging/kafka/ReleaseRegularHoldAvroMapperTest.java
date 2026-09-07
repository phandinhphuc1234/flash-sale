package com.philia.flashsale.inventory.regularhold.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.contract.regularhold.command.v1.ReleaseRegularStockHoldDataV1;
import com.philia.flashsale.contract.regularhold.command.v1.ReleaseRegularStockHoldV1;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class ReleaseRegularHoldAvroMapperTest {
    private final ReleaseRegularHoldAvroMapper mapper = new ReleaseRegularHoldAvroMapper();

    @Test
    void mapsAReleaseCommandAndPreservesItsSourcePosition() {
        UUID sagaId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        ReleaseRegularStockHoldV1 value = new ReleaseRegularStockHoldV1(UUID.randomUUID(),
                "ReleaseRegularStockHold", 1, "order-service", "PURCHASE_SAGA", sagaId, 4L,
                purchaseRequestId, UUID.randomUUID(), Instant.parse("2026-09-04T13:31:00Z"), null, null,
                new ReleaseRegularStockHoldDataV1(sagaId, orderId, purchaseRequestId, holdId, paymentId,
                        "PAYMENT_DEADLINE_EXPIRED", "CANCELLED"));

        var message = mapper.map(new ConsumerRecord<>("flashsale.inventory.regular-hold.commands.v1", 2, 11L,
                orderId.toString(), value));

        assertThat(message.command().holdId()).isEqualTo(holdId);
        assertThat(message.command().paymentId()).isEqualTo(paymentId);
        assertThat(message.command().reason()).isEqualTo("PAYMENT_DEADLINE_EXPIRED");
        assertThat(message.sourcePartition()).isEqualTo(2);
        assertThat(message.sourceOffset()).isEqualTo(11L);
        assertThat(message.payloadFingerprint()).matches("[0-9a-f]{64}");
    }

    @Test
    void rejectsAReleaseCommandWithAnUnsupportedDesiredOrderStatus() {
        UUID sagaId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        ReleaseRegularStockHoldV1 value = new ReleaseRegularStockHoldV1(UUID.randomUUID(),
                "ReleaseRegularStockHold", 1, "order-service", "PURCHASE_SAGA", sagaId, 1L,
                purchaseRequestId, UUID.randomUUID(), Instant.parse("2026-09-04T13:31:00Z"), null, null,
                new ReleaseRegularStockHoldDataV1(sagaId, orderId, purchaseRequestId, UUID.randomUUID(),
                        UUID.randomUUID(), "PAYMENT_DEADLINE_EXPIRED", "CONFIRMED"));

        assertThatThrownBy(() -> mapper.map(new ConsumerRecord<>("topic", 0, 0L, orderId.toString(), value)))
                .isInstanceOf(RegularHoldCommandRecordException.class)
                .hasMessageContaining("correlation or policy");
    }
}
