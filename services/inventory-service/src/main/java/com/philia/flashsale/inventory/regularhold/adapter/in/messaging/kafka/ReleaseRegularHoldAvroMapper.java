package com.philia.flashsale.inventory.regularhold.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.regularhold.command.v1.ReleaseRegularStockHoldV1;
import com.philia.flashsale.inventory.regularhold.application.command.ReleaseRegularStockHoldCommand;
import com.philia.flashsale.inventory.regularhold.application.model.ReleaseRegularHoldMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** Validates the versioned Order release command before it crosses into Inventory application code. */
@Component
public class ReleaseRegularHoldAvroMapper {
    private static final String EVENT_TYPE = "ReleaseRegularStockHold";
    private static final String PRODUCER = "order-service";
    private static final String AGGREGATE_TYPE = "PURCHASE_SAGA";

    public ReleaseRegularHoldMessage map(ConsumerRecord<String, ReleaseRegularStockHoldV1> record) {
        if (record == null || record.value() == null) {
            throw new RegularHoldCommandRecordException("Regular hold release command is missing");
        }
        ReleaseRegularStockHoldV1 value = record.value();
        var data = value.getData();
        if (!EVENT_TYPE.equals(value.getEventType()) || value.getEventVersion() != 1
                || !PRODUCER.equals(value.getProducer()) || !AGGREGATE_TYPE.equals(value.getAggregateType())
                || value.getAggregateVersion() <= 0 || data == null || value.getEventId() == null
                || value.getAggregateId() == null || value.getCorrelationId() == null
                || value.getCausationId() == null || value.getOccurredAt() == null
                || data.getSagaId() == null || data.getOrderId() == null
                || data.getPurchaseRequestId() == null || data.getHoldId() == null
                || data.getPaymentId() == null || data.getReason() == null
                || data.getDesiredOrderStatus() == null) {
            throw new RegularHoldCommandRecordException("Regular hold release command envelope is invalid");
        }
        if (!value.getAggregateId().equals(data.getSagaId())
                || !value.getCorrelationId().equals(data.getPurchaseRequestId())
                || !data.getOrderId().toString().equals(record.key())
                || data.getReason().isBlank() || data.getReason().length() > 80
                || (!"CANCELLED".equals(data.getDesiredOrderStatus())
                        && !"EXPIRED".equals(data.getDesiredOrderStatus()))) {
            throw new RegularHoldCommandRecordException("Regular hold release command correlation or policy is invalid");
        }
        validateTrace(value.getTraceparent(), value.getTracestate());
        ReleaseRegularStockHoldCommand command = new ReleaseRegularStockHoldCommand(value.getEventId(),
                data.getHoldId(), data.getPurchaseRequestId(), data.getOrderId(), data.getPaymentId(),
                data.getReason(), data.getDesiredOrderStatus());
        return new ReleaseRegularHoldMessage(command, value.getAggregateVersion(), fingerprint(value), record.topic(),
                record.partition(), record.offset(), value.getTraceparent(), value.getTracestate());
    }

    private void validateTrace(String traceparent, String tracestate) {
        if (traceparent != null && traceparent.length() > 256) {
            throw new RegularHoldCommandRecordException("traceparent exceeds the bounded contract");
        }
        if (tracestate != null && tracestate.length() > 512) {
            throw new RegularHoldCommandRecordException("tracestate exceeds the bounded contract");
        }
    }

    private String fingerprint(ReleaseRegularStockHoldV1 value) {
        var data = value.getData();
        String canonical = String.join("|", value.getEventId().toString(), value.getEventType(),
                Integer.toString(value.getEventVersion()), value.getProducer(), value.getAggregateType(),
                value.getAggregateId().toString(), Long.toString(value.getAggregateVersion()),
                value.getCorrelationId().toString(), value.getCausationId().toString(), value.getOccurredAt().toString(),
                data.getSagaId().toString(), data.getOrderId().toString(), data.getPurchaseRequestId().toString(),
                data.getHoldId().toString(), data.getPaymentId().toString(), data.getReason(),
                data.getDesiredOrderStatus(), value.getTraceparent() == null ? "" : value.getTraceparent(),
                value.getTracestate() == null ? "" : value.getTracestate());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte valueByte : digest) {
                result.append(String.format("%02x", valueByte));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
