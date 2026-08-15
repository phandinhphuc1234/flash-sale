package com.philia.flashsale.order.order.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedDataV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1;
import com.philia.flashsale.order.order.application.exception.RetryableOrderPersistenceException;
import com.philia.flashsale.order.order.application.port.in.CreateOrderFromAcceptedPurchaseUseCase;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;
import org.springframework.kafka.support.Acknowledgment;

class PurchaseAcceptedKafkaConsumerTests {

    private static final String TOPIC = "flashsale.purchase.events.v1";
    private static final Instant ACCEPTED = Instant.parse("2030-01-01T10:00:00Z");
    private static final String TRACEPARENT = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";

    @Mock
    private CreateOrderFromAcceptedPurchaseUseCase useCase;
    @Mock
    private Acknowledgment acknowledgment;

    private PurchaseAcceptedKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        consumer = new PurchaseAcceptedKafkaConsumer(new PurchaseAcceptedAvroMapper(TOPIC), useCase);
    }

    @Test
    void acknowledgesCreatedAndReplayOutcomesOnlyAfterUseCaseReturns() {
        when(useCase.create(any())).thenAnswer(invocation -> {
            assertThat(MDC.get("traceId")).isEqualTo(TRACEPARENT.substring(3, 35));
            assertThat(MDC.get("traceparent")).isEqualTo(TRACEPARENT);
            return OrderCreationResult.created(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64));
        });

        consumer.onMessage(record(TRACEPARENT), acknowledgment);

        verify(useCase).create(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void classifiesConflictAsNonRetryableAndDoesNotAcknowledge() {
        when(useCase.create(any())).thenReturn(
                OrderCreationResult.conflict(UUID.randomUUID(), "a".repeat(64), "contradictory identity"));

        assertThatThrownBy(() -> consumer.onMessage(record(TRACEPARENT), acknowledgment))
                .isInstanceOf(PurchaseAcceptedConflictException.class)
                .hasMessageContaining("contradictory identity");
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void propagatesRetryableStorageFailureWithoutAcknowledgement() {
        RetryableOrderPersistenceException failure = new RetryableOrderPersistenceException("database unavailable", null);
        when(useCase.create(any())).thenThrow(failure);

        assertThatThrownBy(() -> consumer.onMessage(record(TRACEPARENT), acknowledgment)).isSameAs(failure);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void poisonInputIsRejectedAndTraceIsGeneratedWhenHeaderIsInvalid() {
        ConsumerRecord<String, PurchaseAcceptedV1> record = record("not-a-traceparent");
        when(useCase.create(any())).thenAnswer(invocation -> {
            assertThat(MDC.get("traceId")).hasSize(32);
            assertThat(MDC.get("traceparent")).startsWith("00-");
            return OrderCreationResult.eventReplayed(UUID.randomUUID(), "b".repeat(64));
        });

        consumer.onMessage(record, acknowledgment);

        verify(acknowledgment).acknowledge();
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void invalidWireRecordDoesNotInvokeUseCaseOrAcknowledge() {
        UUID purchaseRequestId = UUID.randomUUID();
        ConsumerRecord<String, PurchaseAcceptedV1> record = new ConsumerRecord<>(TOPIC, 0, 0L,
                purchaseRequestId.toString(), null);

        assertThatThrownBy(() -> consumer.onMessage(record, acknowledgment))
                .isInstanceOf(PurchaseAcceptedRecordException.class);
        verify(useCase, never()).create(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void acknowledgesBusinessReplayAfterTheDurableUseCaseReturns() {
        when(useCase.create(any())).thenReturn(
                OrderCreationResult.businessReplayed(UUID.randomUUID(), "c".repeat(64)));

        consumer.onMessage(record(TRACEPARENT), acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    private ConsumerRecord<String, PurchaseAcceptedV1> record(String traceparent) {
        UUID purchaseRequestId = UUID.randomUUID();
        PurchaseAcceptedV1 event = new PurchaseAcceptedV1(UUID.randomUUID(), "PurchaseAccepted", 1,
                "flashsale-service", "PURCHASE_REQUEST", purchaseRequestId, 1L, UUID.randomUUID(), null,
                ACCEPTED, new PurchaseAcceptedDataV1(purchaseRequestId, UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID(), 1L, new BigDecimal("2.0000"), "VND", ACCEPTED,
                        ACCEPTED.plusSeconds(300)));
        ConsumerRecord<String, PurchaseAcceptedV1> record = new ConsumerRecord<>(TOPIC, 0, 0L,
                purchaseRequestId.toString(), event);
        record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
