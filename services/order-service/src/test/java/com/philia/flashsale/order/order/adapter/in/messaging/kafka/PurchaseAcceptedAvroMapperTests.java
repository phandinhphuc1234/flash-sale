package com.philia.flashsale.order.order.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedDataV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1;
import com.philia.flashsale.order.order.application.command.CreateOrderFromAcceptedPurchaseCommand;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PurchaseAcceptedAvroMapperTests {

    private static final String TOPIC = "flashsale.purchase.events.v1";
    private static final Instant ACCEPTED = Instant.parse("2030-01-01T10:00:00Z");
    private static final String TRACEPARENT = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";
    private PurchaseAcceptedAvroMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PurchaseAcceptedAvroMapper(TOPIC);
    }

    @Test
    void mapsEveryWireFieldAndNormalizesDecimalAndHeaders() {
        UUID purchaseRequestId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        PurchaseAcceptedV1 event = event(eventId, purchaseRequestId, new BigDecimal("12.5"));
        ConsumerRecord<String, PurchaseAcceptedV1> record = record(purchaseRequestId, event);
        record.headers().add("traceparent", TRACEPARENT.getBytes(StandardCharsets.UTF_8));
        record.headers().add("tracestate", "vendor=value".getBytes(StandardCharsets.UTF_8));

        CreateOrderFromAcceptedPurchaseCommand command = mapper.map(record);

        assertThat(command.eventId()).isEqualTo(eventId);
        assertThat(command.purchaseRequestId()).isEqualTo(purchaseRequestId);
        assertThat(command.reservationId()).isEqualTo(event.getData().getReservationId());
        assertThat(command.quantity()).isEqualTo(2);
        assertThat(command.unitPrice()).isEqualByComparingTo("12.5000");
        assertThat(command.acceptedAt()).isEqualTo(ACCEPTED);
        assertThat(command.expiresAt()).isEqualTo(ACCEPTED.plusSeconds(300));
        assertThat(command.sourceTopic()).isEqualTo(TOPIC);
        assertThat(command.sourcePartition()).isZero();
        assertThat(command.sourceOffset()).isEqualTo(42);
        assertThat(command.traceparent()).isEqualTo(TRACEPARENT);
        assertThat(command.tracestate()).isEqualTo("vendor=value");
    }

    @Test
    void rejectsKeyAndAggregateIdentityMismatchBeforeCreatingCommand() {
        UUID purchaseRequestId = UUID.randomUUID();

        assertThatThrownBy(() -> mapper.map(record(UUID.randomUUID(), event(
                UUID.randomUUID(), purchaseRequestId, new BigDecimal("1.0000")))))
                .isInstanceOf(PurchaseAcceptedRecordException.class)
                .hasMessageContaining("must match");
    }

    @Test
    void rejectsUnsupportedEnvelopeAndInvalidBusinessValuesAsNonRetryable() {
        UUID purchaseRequestId = UUID.randomUUID();
        PurchaseAcceptedV1 wrongType = new PurchaseAcceptedV1(
                UUID.randomUUID(), "PaymentRequested", 1, "flashsale-service", "PURCHASE_REQUEST",
                purchaseRequestId, 1L, UUID.randomUUID(), null, ACCEPTED,
                data(purchaseRequestId, 2, new BigDecimal("1.0000"), "VND"));

        assertThatThrownBy(() -> mapper.map(record(purchaseRequestId, wrongType)))
                .isInstanceOf(PurchaseAcceptedRecordException.class);

        PurchaseAcceptedV1 badMoney = event(UUID.randomUUID(), purchaseRequestId, new BigDecimal("1.00001"));
        assertThatThrownBy(() -> mapper.map(record(purchaseRequestId, badMoney)))
                .isInstanceOf(PurchaseAcceptedRecordException.class)
                .hasMessageContaining("scale 4");
    }

    @Test
    void rejectsWrongTtlAndNullValueWithoutLeakingInfrastructureTypes() {
        UUID purchaseRequestId = UUID.randomUUID();
        PurchaseAcceptedV1 wrongTtl = new PurchaseAcceptedV1(
                UUID.randomUUID(), "PurchaseAccepted", 1, "flashsale-service", "PURCHASE_REQUEST",
                purchaseRequestId, 1L, UUID.randomUUID(), null, ACCEPTED,
                new PurchaseAcceptedDataV1(purchaseRequestId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), 1L, new BigDecimal("1.0000"), "VND", ACCEPTED,
                        ACCEPTED.plusSeconds(301)));

        assertThatThrownBy(() -> mapper.map(record(purchaseRequestId, wrongTtl)))
                .isInstanceOf(PurchaseAcceptedRecordException.class);
        ConsumerRecord<String, PurchaseAcceptedV1> nullRecord = new ConsumerRecord<>(TOPIC, 0, 1L,
                purchaseRequestId.toString(), null);
        assertThatThrownBy(() -> mapper.map(nullRecord)).isInstanceOf(PurchaseAcceptedRecordException.class);
        assertThat(CreateOrderFromAcceptedPurchaseCommand.class.getPackageName())
                .isEqualTo("com.philia.flashsale.order.order.application.command");
    }

    private ConsumerRecord<String, PurchaseAcceptedV1> record(UUID key, PurchaseAcceptedV1 event) {
        return new ConsumerRecord<>(TOPIC, 0, 42L, key.toString(), event);
    }

    private PurchaseAcceptedV1 event(UUID eventId, UUID purchaseRequestId, BigDecimal price) {
        return new PurchaseAcceptedV1(eventId, "PurchaseAccepted", 1, "flashsale-service", "PURCHASE_REQUEST",
                purchaseRequestId, 1L, UUID.randomUUID(), null, ACCEPTED,
                data(purchaseRequestId, 2, price, "VND"));
    }

    private PurchaseAcceptedDataV1 data(UUID purchaseRequestId, long quantity, BigDecimal price, String currency) {
        return new PurchaseAcceptedDataV1(purchaseRequestId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), quantity, price, currency, ACCEPTED, ACCEPTED.plusSeconds(300));
    }
}
