package com.philia.flashsale.cart.adapter.in.messaging.kafka;

import com.philia.flashsale.cart.application.command.ReconcilePurchasedCartSnapshotCommand;
import com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotV1;
import java.util.Objects;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/** Validates the versioned reconciliation envelope before it reaches Cart application code. */
public final class ReconcilePurchasedCartSnapshotAvroMapper {
    public ReconcilePurchasedCartSnapshotCommand map(ConsumerRecord<String, ReconcilePurchasedCartSnapshotV1> record) {
        Objects.requireNonNull(record, "record");
        var event = Objects.requireNonNull(record.value(), "record.value");
        if (!"ReconcilePurchasedCartSnapshot".equals(event.getEventType()) || event.getEventVersion() != 1
                || !"order-service".equals(event.getProducer()) || !"ORDER".equals(event.getAggregateType())
                || event.getData() == null || event.getAggregateId() == null || event.getCausationId() == null) {
            throw new CartReconciliationRecordException("invalid reconciliation envelope");
        }
        var data = event.getData();
        if (!event.getAggregateId().equals(data.getOrderId()) || !event.getCorrelationId().equals(data.getPurchaseRequestId())
                || data.getItems() == null || data.getItems().isEmpty()) {
            throw new CartReconciliationRecordException("reconciliation identity mismatch");
        }
        var items = data.getItems().stream().map(item -> new ReconcilePurchasedCartSnapshotCommand.Item(
                item.getVariantId(), item.getQuantity(), item.getItemVersion())).toList();
        return new ReconcilePurchasedCartSnapshotCommand(event.getEventId(), data.getOrderId(),
                data.getPurchaseRequestId(), data.getCartId(), data.getOwnerId(), data.getSnapshotCartVersion(),
                data.getConfirmedAt(), items, record.topic(), record.partition(), record.offset(),
                event.getTraceparent(), event.getTracestate());
    }
}
