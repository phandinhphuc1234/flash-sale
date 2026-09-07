package com.philia.flashsale.cart.checkout;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.cart.adapter.in.messaging.kafka.CartReconciliationKafkaConsumer;
import com.philia.flashsale.cart.adapter.in.messaging.kafka.CartReconciliationRecordException;
import com.philia.flashsale.cart.adapter.in.messaging.kafka.ReconcilePurchasedCartSnapshotAvroMapper;
import com.philia.flashsale.cart.application.port.in.ReconcilePurchasedCartSnapshotUseCase;
import com.philia.flashsale.cart.application.result.ReconcilePurchasedCartSnapshotResult;
import com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotDataV1;
import com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotItemV1;
import com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotV1;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

/** Failure-path coverage for Cart reconciliation malformed records and replay/conflict boundaries. */
class CartReconciliationKafkaFailureTests {
    private static final String TOPIC = "flashsale.cart.checkout.commands.v1";

    @Test
    void missingAvroValueIsAContractFailureRatherThanATransientNullPointer() {
        var mapper = new ReconcilePurchasedCartSnapshotAvroMapper();

        assertThatThrownBy(() -> mapper.map(new ConsumerRecord<>(TOPIC, 0, 1L, "cart", null)))
                .isInstanceOf(CartReconciliationRecordException.class);
    }

    @Test
    void aggregateAndPayloadIdentityConflictIsRejectedBeforeCartMutation() {
        var event = event();
        var mismatch = ReconcilePurchasedCartSnapshotV1.newBuilder(event)
                .setData(ReconcilePurchasedCartSnapshotDataV1.newBuilder(event.getData())
                        .setPurchaseRequestId(UUID.randomUUID()).build())
                .build();

        assertThatThrownBy(() -> new ReconcilePurchasedCartSnapshotAvroMapper().map(
                new ConsumerRecord<>(TOPIC, 0, 1L, mismatch.getData().getCartId().toString(), mismatch)))
                .isInstanceOf(CartReconciliationRecordException.class)
                .hasMessageContaining("identity");
    }

    @Test
    void persistenceFailureIsNotAcknowledgedSoKafkaCanRetryAndEventuallyUseTheDlt() {
        var event = event();
        var useCase = mock(ReconcilePurchasedCartSnapshotUseCase.class);
        when(useCase.reconcile(any())).thenThrow(new IllegalStateException("database unavailable"));
        var consumer = new CartReconciliationKafkaConsumer(new ReconcilePurchasedCartSnapshotAvroMapper(), useCase);
        var acknowledgment = mock(Acknowledgment.class);

        assertThatThrownBy(() -> consumer.onMessage(new ConsumerRecord<>(TOPIC, 0, 2L,
                event.getData().getCartId().toString(), event), acknowledgment))
                .isInstanceOf(IllegalStateException.class);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void successfulReconciliationIsAcknowledgedAfterTheUseCaseReturns() {
        var event = event();
        var useCase = mock(ReconcilePurchasedCartSnapshotUseCase.class);
        when(useCase.reconcile(any())).thenReturn(new ReconcilePurchasedCartSnapshotResult(
                ReconcilePurchasedCartSnapshotResult.Outcome.APPLIED, 1));
        var consumer = new CartReconciliationKafkaConsumer(new ReconcilePurchasedCartSnapshotAvroMapper(), useCase);
        var acknowledgment = mock(Acknowledgment.class);

        consumer.onMessage(new ConsumerRecord<>(TOPIC, 0, 2L, event.getData().getCartId().toString(), event), acknowledgment);

        verify(useCase).reconcile(any());
        verify(acknowledgment).acknowledge();
    }

    private ReconcilePurchasedCartSnapshotV1 event() {
        UUID orderId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-05T00:00:00Z");
        var data = new ReconcilePurchasedCartSnapshotDataV1(orderId, requestId, cartId, UUID.randomUUID(), 2L, now,
                List.of(new ReconcilePurchasedCartSnapshotItemV1(UUID.randomUUID(), 1L, 1L)));
        return new ReconcilePurchasedCartSnapshotV1(UUID.randomUUID(), "ReconcilePurchasedCartSnapshot", 1,
                "order-service", "ORDER", orderId, 1L, requestId, UUID.randomUUID(), now, null, null, data);
    }
}
