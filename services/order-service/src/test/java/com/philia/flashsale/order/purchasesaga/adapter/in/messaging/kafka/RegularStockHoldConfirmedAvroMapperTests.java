package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedDataV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedItemV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedV1;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class RegularStockHoldConfirmedAvroMapperTests {
    private static final String TOPIC = "flashsale.inventory.regular-hold.events.v1";
    private final RegularStockHoldConfirmedAvroMapper mapper = new RegularStockHoldConfirmedAvroMapper(TOPIC);

    @Test
    void mapsTheExactHoldOrderPaymentAndCommandIdentity() {
        var event = event();
        var mapped = mapper.map(record(event));
        assertThat(mapped.holdId()).isEqualTo(event.getAggregateId());
        assertThat(mapped.orderId()).isEqualTo(event.getData().getOrderId());
        assertThat(mapped.paymentId()).isEqualTo(event.getData().getPaymentId());
        assertThat(mapped.causationId()).isEqualTo(event.getCausationId());
        assertThat(mapped.fingerprint()).hasSize(64);
    }

    @Test
    void fingerprintIsIndependentOfLineOrderAndSourceOffset() {
        var event = event();
        var first = mapper.map(record(event));
        event = RegularStockHoldConfirmedV1.newBuilder(event).setData(
                RegularStockHoldConfirmedDataV1.newBuilder(event.getData())
                        .setItems(event.getData().getItems().reversed()).build()).build();
        var reordered = mapper.map(new ConsumerRecord<>(TOPIC, 0, 99,
                event.getData().getOrderId().toString(), event));
        assertThat(reordered.fingerprint()).isEqualTo(first.fingerprint());
    }

    @Test
    void rejectsWrongKeyProducerAndDuplicateVariants() {
        var event = event();
        assertThatThrownBy(() -> mapper.map(new ConsumerRecord<>(TOPIC, 0, 1,
                UUID.randomUUID().toString(), event))).isInstanceOf(RegularStockHoldConfirmedRecordException.class);
        var wrongProducer = RegularStockHoldConfirmedV1.newBuilder(event).setProducer("payment-service").build();
        assertThatThrownBy(() -> mapper.map(record(wrongProducer))).isInstanceOf(RegularStockHoldConfirmedRecordException.class);
        var item = event.getData().getItems().getFirst();
        var duplicateItems = RegularStockHoldConfirmedV1.newBuilder(event).setData(
                RegularStockHoldConfirmedDataV1.newBuilder(event.getData()).setItems(List.of(item, item)).build()).build();
        assertThatThrownBy(() -> mapper.map(record(duplicateItems))).isInstanceOf(RegularStockHoldConfirmedRecordException.class);
    }

    private ConsumerRecord<String, RegularStockHoldConfirmedV1> record(RegularStockHoldConfirmedV1 event) {
        return new ConsumerRecord<>(TOPIC, 0, 1, event.getData().getOrderId().toString(), event);
    }

    private RegularStockHoldConfirmedV1 event() {
        UUID hold = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        Instant now = Instant.parse("2032-01-01T00:00:00Z");
        var data = RegularStockHoldConfirmedDataV1.newBuilder()
                .setHoldId(hold).setPurchaseRequestId(request).setOrderId(UUID.randomUUID())
                .setPaymentId(UUID.randomUUID()).setStatus("CONFIRMED").setTransitionedAt(now)
                .setItems(List.of(
                        RegularStockHoldConfirmedItemV1.newBuilder().setVariantId(UUID.randomUUID()).setQuantity(1).build(),
                        RegularStockHoldConfirmedItemV1.newBuilder().setVariantId(UUID.randomUUID()).setQuantity(2).build()))
                .build();
        return RegularStockHoldConfirmedV1.newBuilder()
                .setEventId(UUID.randomUUID()).setEventType("RegularStockHoldConfirmed").setEventVersion(1)
                .setProducer("inventory-service").setAggregateType("REGULAR_STOCK_HOLD")
                .setAggregateId(hold).setAggregateVersion(2).setCorrelationId(request)
                .setCausationId(UUID.randomUUID()).setOccurredAt(now).setData(data).build();
    }
}
