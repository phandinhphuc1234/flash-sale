package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationReleasedDataV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationReleasedV1;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class PurchaseReservationReleasedAvroMapperTests {
    private static final String TOPIC = "flashsale.purchase.events.v1";
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void mapsExpiredResultAndKeepsReservationAggregateSeparateFromOrderKey() {
        UUID orderId = UUID.randomUUID(); UUID sagaId = UUID.randomUUID(); UUID reservationId = UUID.randomUUID();
        var command = new PurchaseReservationReleasedAvroMapper(topicProperties()).map(
                new ConsumerRecord<>(TOPIC, 0, 8L, orderId.toString(), event(orderId, sagaId, reservationId, "EXPIRED")));
        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.reservationId()).isEqualTo(reservationId);
        assertThat(command.reservationStatus()).isEqualTo("EXPIRED");
        assertThat(command.fingerprint()).hasSize(64);
    }

    @Test
    void rejectsAggregateIdentityThatIsNotTheReservation() {
        UUID orderId = UUID.randomUUID(); UUID sagaId = UUID.randomUUID(); UUID reservationId = UUID.randomUUID();
        var invalid = new PurchaseReservationReleasedV1(UUID.randomUUID(), "PurchaseReservationReleased", 1,
                "flashsale-service", "PURCHASE_RESERVATION", UUID.randomUUID(), 2L, sagaId, UUID.randomUUID(), NOW,
                new PurchaseReservationReleasedDataV1(sagaId, orderId, sagaId, reservationId, "RELEASED",
                        "PROVIDER_TERMINAL_FAILURE", NOW.plusSeconds(1)));
        assertThatThrownBy(() -> new PurchaseReservationReleasedAvroMapper(topicProperties()).map(
                new ConsumerRecord<>(TOPIC, 0, 0L, orderId.toString(), invalid)))
                .isInstanceOf(PurchaseReservationReleasedRecordException.class)
                .hasMessageContaining("aggregateId");
    }

    private com.philia.flashsale.order.configuration.OrderKafkaProperties topicProperties() {
        return new com.philia.flashsale.order.configuration.OrderKafkaProperties(
                "localhost:9092", "http://localhost:8081", "accepted", "group", "dlt",
                "orders", java.util.List.of(java.time.Duration.ofMillis(1)), "commands");
    }

    private PurchaseReservationReleasedV1 event(UUID orderId, UUID sagaId, UUID reservationId, String status) {
        return new PurchaseReservationReleasedV1(UUID.randomUUID(), "PurchaseReservationReleased", 1,
                "flashsale-service", "PURCHASE_RESERVATION", reservationId, 2L, sagaId, UUID.randomUUID(), NOW,
                new PurchaseReservationReleasedDataV1(sagaId, orderId, sagaId, reservationId, status,
                        "PAYMENT_DEADLINE_EXPIRED", NOW.plusSeconds(1)));
    }
}
