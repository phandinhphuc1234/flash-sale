package com.philia.flashsale.inventory.regularhold.adapter.out.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldReleasedV1;
import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldFactItem;
import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldFactOutboxEvent;
import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldOutboxEvent;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHoldStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegularHoldOutcomeAvroMapperTest {
    private static final Instant NOW = Instant.parse("2026-09-04T13:31:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RegularHoldOutcomeAvroMapper mapper = new RegularHoldOutcomeAvroMapper(objectMapper);

    @Test
    void mapsTheDurableConfirmedFactToTheExactV1SpecificRecord() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        RegularHoldFactOutboxEvent event = new RegularHoldFactOutboxEvent(eventId,
                "RegularStockHoldConfirmed", 2L, holdId, purchaseRequestId, orderId, UUID.randomUUID(), null,
                null, RegularStockHoldStatus.CONFIRMED,
                List.of(new RegularHoldFactItem(UUID.randomUUID(), 2L)), paymentId, NOW);
        RegularHoldOutboxEvent outbox = new RegularHoldOutboxEvent(eventId, event.eventType(),
                event.aggregateVersion(), holdId, orderId.toString(), purchaseRequestId, event.causationId(),
                null, null, objectMapper.writeValueAsString(event));

        var avro = mapper.toRecord(outbox);

        assertThat(avro).isInstanceOf(RegularStockHoldConfirmedV1.class);
        RegularStockHoldConfirmedV1 confirmed = (RegularStockHoldConfirmedV1) avro;
        assertThat(confirmed.getEventId()).isEqualTo(eventId);
        assertThat(confirmed.getAggregateId()).isEqualTo(holdId);
        assertThat(confirmed.getCorrelationId()).isEqualTo(purchaseRequestId);
        assertThat(confirmed.getData().getOrderId()).isEqualTo(orderId);
        assertThat(confirmed.getData().getPaymentId()).isEqualTo(paymentId);
        assertThat(confirmed.getData().getItems()).hasSize(1);
    }

    @Test
    void mapsTheDurableReleasedFactWithItsBoundedReason() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        RegularHoldFactOutboxEvent event = new RegularHoldFactOutboxEvent(eventId,
                "RegularStockHoldReleased", 3L, holdId, purchaseRequestId, orderId, UUID.randomUUID(), null,
                null, RegularStockHoldStatus.RELEASED,
                List.of(new RegularHoldFactItem(UUID.randomUUID(), 1L)), UUID.randomUUID(), NOW,
                "PAYMENT_DEADLINE_EXPIRED");
        RegularHoldOutboxEvent outbox = new RegularHoldOutboxEvent(eventId, event.eventType(),
                event.aggregateVersion(), holdId, orderId.toString(), purchaseRequestId, event.causationId(),
                null, null, objectMapper.writeValueAsString(event));

        var avro = mapper.toRecord(outbox);

        assertThat(avro).isInstanceOf(RegularStockHoldReleasedV1.class);
        RegularStockHoldReleasedV1 released = (RegularStockHoldReleasedV1) avro;
        assertThat(released.getData().getStatus()).isEqualTo("RELEASED");
        assertThat(released.getData().getReason()).isEqualTo("PAYMENT_DEADLINE_EXPIRED");
        assertThat(released.getData().getItems()).hasSize(1);
    }
}
