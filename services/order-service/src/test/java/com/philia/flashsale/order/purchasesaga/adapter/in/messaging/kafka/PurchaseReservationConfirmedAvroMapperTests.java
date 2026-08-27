package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationConfirmedDataV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationConfirmedV1;
import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationConfirmedCommand;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class PurchaseReservationConfirmedAvroMapperTests {
    private static final String TOPIC = "flashsale.purchase.events.v1";
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void mapsConfirmationAndKeepsSagaAndOrderIdentities() {
        UUID orderId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        var event = event(orderId, sagaId, reservationId, paymentId);

        PurchaseReservationConfirmedCommand command = new PurchaseReservationConfirmedAvroMapper(TOPIC)
                .map(new ConsumerRecord<>(TOPIC, 0, 4, orderId.toString(), event));

        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.sagaId()).isEqualTo(sagaId);
        assertThat(command.reservationId()).isEqualTo(reservationId);
        assertThat(command.fingerprint()).hasSize(64);
    }

    @Test
    void rejectsAConfirmationWhoseKafkaKeyDoesNotMatchTheOrder() {
        UUID orderId = UUID.randomUUID();
        var event = event(orderId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> new PurchaseReservationConfirmedAvroMapper(TOPIC).map(
                new ConsumerRecord<>(TOPIC, 0, 0, UUID.randomUUID().toString(), event)))
                .isInstanceOf(PurchaseReservationConfirmedRecordException.class)
                .hasMessageContaining("message key");
    }

    @Test
    void rejectsAConfirmationWithAnAggregateThatIsNotTheReservation() {
        UUID orderId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        var event = new PurchaseReservationConfirmedV1(UUID.randomUUID(), "PurchaseReservationConfirmed", 1,
                "flashsale-service", "PURCHASE_RESERVATION", UUID.randomUUID(), 2L, sagaId,
                UUID.randomUUID(), NOW,
                new PurchaseReservationConfirmedDataV1(sagaId, orderId, sagaId, reservationId,
                        UUID.randomUUID(), NOW.plusSeconds(1)));

        assertThatThrownBy(() -> new PurchaseReservationConfirmedAvroMapper(TOPIC).map(
                new ConsumerRecord<>(TOPIC, 0, 0, orderId.toString(), event)))
                .isInstanceOf(PurchaseReservationConfirmedRecordException.class)
                .hasMessageContaining("aggregateId");
    }

    private PurchaseReservationConfirmedV1 event(UUID orderId, UUID sagaId, UUID reservationId,
            UUID paymentId) {
        return new PurchaseReservationConfirmedV1(UUID.randomUUID(), "PurchaseReservationConfirmed", 1,
                "flashsale-service", "PURCHASE_RESERVATION", reservationId, 2L, sagaId,
                UUID.randomUUID(), NOW,
                new PurchaseReservationConfirmedDataV1(sagaId, orderId, sagaId, reservationId,
                        paymentId, NOW.plusSeconds(1)));
    }
}
