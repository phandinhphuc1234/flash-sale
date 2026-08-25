package com.philia.flashsale.flashsale.reservation.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationDataV1;
import com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationV1;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class ConfirmReservationAvroMapperTests {
    private static final String TOPIC = "flashsale.purchase.commands.v1";
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void mapsConfirmCommandAndRetainsSourcePositionFingerprint() {
        UUID orderId = UUID.randomUUID();
        var record = new ConsumerRecord<>(TOPIC, 2, 17L, orderId.toString(), event(orderId));

        var command = new ConfirmReservationAvroMapper(properties()).map(record);

        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.sourcePartition()).isEqualTo(2);
        assertThat(command.sourceOffset()).isEqualTo(17L);
        assertThat(command.payloadFingerprint()).hasSize(64);
    }

    @Test
    void rejectsACommandWhoseKafkaKeyDoesNotMatchTheOrder() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> new ConfirmReservationAvroMapper(properties()).map(
                new ConsumerRecord<>(TOPIC, 0, 0L, UUID.randomUUID().toString(), event(orderId))))
                .isInstanceOf(ConfirmReservationRecordException.class)
                .hasMessageContaining("message key");
    }

    private ReservationCommandProperties properties() {
        return new ReservationCommandProperties(TOPIC, "group", "dlt", List.of(Duration.ofMillis(1)));
    }

    private ConfirmPurchaseReservationV1 event(UUID orderId) {
        UUID sagaId = UUID.randomUUID();
        return new ConfirmPurchaseReservationV1(UUID.randomUUID(), "ConfirmPurchaseReservation", 1,
                "order-service", "PURCHASE_SAGA", sagaId, 3L, orderId, UUID.randomUUID(), NOW,
                new ConfirmPurchaseReservationDataV1(sagaId, orderId, UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), NOW.plusSeconds(1)));
    }
}
