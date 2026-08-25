package com.philia.flashsale.flashsale.reservation.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.contract.purchase.command.v1.ReleasePurchaseReservationDataV1;
import com.philia.flashsale.contract.purchase.command.v1.ReleasePurchaseReservationV1;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class ReleaseReservationAvroMapperTests {
    private static final String TOPIC = "flashsale.purchase.commands.v1";
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void mapsReleaseCommandAndPreservesStableSourceIdentity() {
        UUID orderId = UUID.randomUUID();
        var command = new ReleaseReservationAvroMapper(properties()).map(
                new ConsumerRecord<>(TOPIC, 2, 17L, orderId.toString(), event(orderId, "PROVIDER_TERMINAL_FAILURE")));
        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.reason()).isEqualTo("PROVIDER_TERMINAL_FAILURE");
        assertThat(command.payloadFingerprint()).hasSize(64);
    }

    @Test
    void rejectsUnknownReleaseReason() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> new ReleaseReservationAvroMapper(properties()).map(
                new ConsumerRecord<>(TOPIC, 0, 0L, orderId.toString(), event(orderId, "CARD_DECLINED"))))
                .isInstanceOf(ReleaseReservationRecordException.class)
                .hasMessageContaining("reason");
    }

    private ReservationCommandProperties properties() {
        return new ReservationCommandProperties(TOPIC, "group", "dlt", List.of(Duration.ofMillis(1)));
    }

    private ReleasePurchaseReservationV1 event(UUID orderId, String reason) {
        UUID sagaId = UUID.randomUUID();
        return new ReleasePurchaseReservationV1(UUID.randomUUID(), "ReleasePurchaseReservation", 1,
                "order-service", "PURCHASE_SAGA", sagaId, 2L, sagaId, UUID.randomUUID(), NOW,
                new ReleasePurchaseReservationDataV1(sagaId, orderId, sagaId, UUID.randomUUID(), reason));
    }
}
