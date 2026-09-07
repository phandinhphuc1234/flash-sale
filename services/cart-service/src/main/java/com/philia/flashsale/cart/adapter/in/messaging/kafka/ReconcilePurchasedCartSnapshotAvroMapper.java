package com.philia.flashsale.cart.adapter.in.messaging.kafka;

import com.philia.flashsale.cart.application.command.ReconcilePurchasedCartSnapshotCommand;
import com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotV1;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/** Validates the versioned reconciliation envelope before it reaches Cart application code. */
public final class ReconcilePurchasedCartSnapshotAvroMapper {
    public ReconcilePurchasedCartSnapshotCommand map(ConsumerRecord<String, ReconcilePurchasedCartSnapshotV1> record) {
        if (record == null || record.value() == null) {
            throw invalid("reconciliation record is missing");
        }
        var event = record.value();
        if (!"ReconcilePurchasedCartSnapshot".equals(event.getEventType()) || event.getEventVersion() != 1
                || !"order-service".equals(event.getProducer()) || !"ORDER".equals(event.getAggregateType())
                || event.getEventId() == null || event.getAggregateId() == null
                || event.getCorrelationId() == null || event.getCausationId() == null
                || event.getOccurredAt() == null || event.getAggregateVersion() <= 0 || event.getData() == null) {
            throw invalid("invalid reconciliation envelope");
        }
        var data = event.getData();
        if (!event.getAggregateId().equals(data.getOrderId()) || !event.getCorrelationId().equals(data.getPurchaseRequestId())
                || data.getCartId() == null || data.getOwnerId() == null || data.getConfirmedAt() == null
                || data.getItems() == null || data.getItems().isEmpty()
                || !data.getCartId().toString().equals(record.key())) {
            throw invalid("reconciliation identity mismatch");
        }
        try {
            var items = data.getItems().stream().map(item -> {
                if (item == null || item.getVariantId() == null) {
                    throw invalid("reconciliation item is missing");
                }
                return new ReconcilePurchasedCartSnapshotCommand.Item(item.getVariantId(), item.getQuantity(),
                        item.getItemVersion());
            }).toList();
            if (items.stream().map(ReconcilePurchasedCartSnapshotCommand.Item::variantId).distinct().count()
                    != items.size()) {
                throw invalid("reconciliation items must be distinct");
            }
            return new ReconcilePurchasedCartSnapshotCommand(event.getEventId(), data.getOrderId(),
                    data.getPurchaseRequestId(), data.getCartId(), data.getOwnerId(), data.getSnapshotCartVersion(),
                    data.getConfirmedAt(), items, record.topic(), record.partition(), record.offset(),
                    event.getTraceparent(), event.getTracestate());
        } catch (CartReconciliationRecordException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid("reconciliation payload is invalid");
        }
    }

    private CartReconciliationRecordException invalid(String message) {
        return new CartReconciliationRecordException(message);
    }
}
