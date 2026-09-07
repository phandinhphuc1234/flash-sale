package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedDataV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedItemV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldReleasedDataV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldReleasedItemV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldReleasedV1;
import com.philia.flashsale.order.observability.OrderObservability;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyRegularHoldConfirmationUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyRegularHoldOutcomeUseCase;
import com.philia.flashsale.order.purchasesaga.application.result.RegularHoldConfirmationResult;
import com.philia.flashsale.order.purchasesaga.application.result.RegularHoldOutcomeResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

/** Failure-path coverage for the strict Order-side regular-hold result consumer. */
class RegularHoldResultKafkaFailureTests {
    private static final String TOPIC = "flashsale.inventory.regular-hold.events.v1";

    @Test
    void unsupportedRecordTypeIsRejectedAndNotAcknowledged() {
        var useCase = mock(ApplyRegularHoldConfirmationUseCase.class);
        var consumer = new RegularHoldResultKafkaConsumer(new RegularStockHoldConfirmedAvroMapper(TOPIC), useCase,
                OrderObservability.noop());
        var acknowledgment = mock(Acknowledgment.class);

        assertThatThrownBy(() -> consumer.onMessage(new ConsumerRecord<>(TOPIC, 0, 1L, "order", "not-avro"),
                acknowledgment)).isInstanceOf(RegularStockHoldConfirmedRecordException.class);
        verify(useCase, never()).apply(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void wrongKafkaIdentityIsRejectedBeforeTheSagaUseCase() {
        var event = event();
        var consumer = new RegularHoldResultKafkaConsumer(new RegularStockHoldConfirmedAvroMapper(TOPIC),
                mock(ApplyRegularHoldConfirmationUseCase.class), OrderObservability.noop());
        var acknowledgment = mock(Acknowledgment.class);

        assertThatThrownBy(() -> consumer.onMessage(new ConsumerRecord<>(TOPIC, 1, 4L,
                UUID.randomUUID().toString(), event), acknowledgment))
                .isInstanceOf(RegularStockHoldConfirmedRecordException.class)
                .hasMessageContaining("identity");
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void identityConflictIsLeftForTheConfiguredRetryAndDltHandler() {
        var event = event();
        var useCase = mock(ApplyRegularHoldConfirmationUseCase.class);
        when(useCase.apply(any())).thenReturn(RegularHoldConfirmationResult.conflict(
                event.getData().getOrderId(), event.getData().getOrderId(), "same version conflict"));
        var consumer = new RegularHoldResultKafkaConsumer(new RegularStockHoldConfirmedAvroMapper(TOPIC), useCase,
                OrderObservability.noop());
        var acknowledgment = mock(Acknowledgment.class);

        assertThatThrownBy(() -> consumer.onMessage(new ConsumerRecord<>(TOPIC, 1, 4L,
                event.getData().getOrderId().toString(), event), acknowledgment))
                .isInstanceOf(RegularStockHoldConfirmedConflictException.class)
                .hasMessageContaining("same version conflict");
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void releasedOutcomeIsMappedAppliedAndAcknowledgedOnlyAfterTheUseCaseReturns() {
        var event = releasedEvent();
        var outcomeUseCase = mock(ApplyRegularHoldOutcomeUseCase.class);
        when(outcomeUseCase.apply(any())).thenReturn(RegularHoldOutcomeResult.applied(
                event.getData().getOrderId(), event.getData().getPurchaseRequestId()));
        var acknowledgment = mock(Acknowledgment.class);
        var consumer = new RegularHoldResultKafkaConsumer(new RegularStockHoldConfirmedAvroMapper(TOPIC),
                mock(ApplyRegularHoldConfirmationUseCase.class), new RegularStockHoldOutcomeAvroMapper(TOPIC),
                outcomeUseCase, OrderObservability.noop());

        consumer.onMessage(new ConsumerRecord<>(TOPIC, 0, 5L, event.getData().getOrderId().toString(), event),
                acknowledgment);

        verify(outcomeUseCase).apply(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void releasedOutcomeConflictIsNotAcknowledgedAndIsLeftForDltRecovery() {
        var event = releasedEvent();
        var outcomeUseCase = mock(ApplyRegularHoldOutcomeUseCase.class);
        when(outcomeUseCase.apply(any())).thenReturn(RegularHoldOutcomeResult.conflict(
                event.getData().getOrderId(), event.getData().getPurchaseRequestId(), "identity conflict"));
        var acknowledgment = mock(Acknowledgment.class);
        var consumer = new RegularHoldResultKafkaConsumer(new RegularStockHoldConfirmedAvroMapper(TOPIC),
                mock(ApplyRegularHoldConfirmationUseCase.class), new RegularStockHoldOutcomeAvroMapper(TOPIC),
                outcomeUseCase, OrderObservability.noop());

        assertThatThrownBy(() -> consumer.onMessage(new ConsumerRecord<>(TOPIC, 0, 5L,
                event.getData().getOrderId().toString(), event), acknowledgment))
                .isInstanceOf(RegularStockHoldOutcomeConflictException.class)
                .hasMessageContaining("identity conflict");
        verify(acknowledgment, never()).acknowledge();
    }

    private RegularStockHoldConfirmedV1 event() {
        UUID holdId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-05T00:00:00Z");
        var data = RegularStockHoldConfirmedDataV1.newBuilder()
                .setHoldId(holdId).setPurchaseRequestId(purchaseRequestId).setOrderId(orderId)
                .setPaymentId(UUID.randomUUID()).setStatus("CONFIRMED").setTransitionedAt(now)
                .setItems(List.of(new RegularStockHoldConfirmedItemV1(UUID.randomUUID(), 1L))).build();
        return new RegularStockHoldConfirmedV1(UUID.randomUUID(), "RegularStockHoldConfirmed", 1,
                "inventory-service", "REGULAR_STOCK_HOLD", holdId, 2L, purchaseRequestId, UUID.randomUUID(), now,
                null, null, data);
    }

    private RegularStockHoldReleasedV1 releasedEvent() {
        UUID holdId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-05T00:00:00Z");
        var data = new RegularStockHoldReleasedDataV1(holdId, purchaseRequestId, orderId, "RELEASED",
                List.of(new RegularStockHoldReleasedItemV1(UUID.randomUUID(), 1L)),
                "PROVIDER_TERMINAL_FAILURE", now);
        return new RegularStockHoldReleasedV1(UUID.randomUUID(), "RegularStockHoldReleased", 1,
                "inventory-service", "REGULAR_STOCK_HOLD", holdId, 2L, purchaseRequestId, UUID.randomUUID(), now,
                null, null, data);
    }
}
