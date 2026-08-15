package com.philia.flashsale.order.order.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1;
import com.philia.flashsale.order.order.application.exception.RetryableOrderPersistenceException;
import com.philia.flashsale.order.order.application.port.in.CreateOrderFromAcceptedPurchaseUseCase;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Inbound Kafka adapter that acknowledges only after the Order transaction returns successfully. */
@Component
@ConditionalOnProperty(name = "order.runtime.accepted-purchase-consumer-enabled", havingValue = "true", matchIfMissing = true)
public final class PurchaseAcceptedKafkaConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseAcceptedKafkaConsumer.class);
    private static final String TRACEPARENT = "traceparent";
    private static final String TRACESTATE = "tracestate";
    private static final String TRACE_ID = "traceId";

    private final PurchaseAcceptedAvroMapper mapper;
    private final CreateOrderFromAcceptedPurchaseUseCase useCase;

    public PurchaseAcceptedKafkaConsumer(PurchaseAcceptedAvroMapper mapper,
            CreateOrderFromAcceptedPurchaseUseCase useCase) {
        this.mapper = mapper;
        this.useCase = useCase;
    }

    @KafkaListener(topics = "${order.kafka.accepted-purchase-topic}",
            groupId = "${order.kafka.accepted-purchase-consumer-group}",
            containerFactory = "orderPurchaseAcceptedKafkaListenerContainerFactory",
            autoStartup = "${order.runtime.accepted-purchase-consumer-enabled:true}")
    public void onMessage(ConsumerRecord<String, PurchaseAcceptedV1> record, Acknowledgment acknowledgment) {
        String traceparent = header(record, TRACEPARENT);
        String effectiveTraceparent = isValidTraceparent(traceparent) ? traceparent : newTraceparent();
        String traceId = effectiveTraceparent.substring(3, 35);
        try (MDC.MDCCloseable trace = MDC.putCloseable(TRACE_ID, traceId);
                MDC.MDCCloseable parent = MDC.putCloseable(TRACEPARENT, effectiveTraceparent);
                MDC.MDCCloseable state = MDC.putCloseable(TRACESTATE, header(record, TRACESTATE))) {
            consume(record, acknowledgment);
        } catch (PurchaseAcceptedRecordException | PurchaseAcceptedConflictException exception) {
            LOGGER.warn("Rejected PurchaseAccepted record eventId={} key={} reason={}", safeEventId(record),
                    record == null ? null : record.key(), exception.getMessage());
            throw exception;
        }
    }

    private void consume(ConsumerRecord<String, PurchaseAcceptedV1> record, Acknowledgment acknowledgment) {
        var command = mapper.map(record);
        OrderCreationResult result = useCase.create(command);
        if (result.outcome() == OrderCreationResult.Outcome.CONFLICT) {
            throw new PurchaseAcceptedConflictException("Order identity conflict: " + result.conflictReason());
        }
        if (result.outcome() != OrderCreationResult.Outcome.CREATED
                && result.outcome() != OrderCreationResult.Outcome.EVENT_REPLAYED
                && result.outcome() != OrderCreationResult.Outcome.BUSINESS_REPLAYED) {
            throw new IllegalStateException("unsupported Order creation outcome");
        }
        // The use case returns only after the @Transactional persistence adapter has committed.
        acknowledgment.acknowledge();
    }

    private String safeEventId(ConsumerRecord<String, PurchaseAcceptedV1> record) {
        if (record == null || record.value() == null) {
            return "unknown";
        }
        UUID eventId = record.value().getEventId();
        return eventId == null ? "unknown" : eventId.toString();
    }

    private String header(ConsumerRecord<String, PurchaseAcceptedV1> record, String name) {
        if (record == null) {
            return null;
        }
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    static boolean isValidTraceparent(String value) {
        if (value == null || !value.matches("^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$")) {
            return false;
        }
        return !value.substring(3, 35).equals("00000000000000000000000000000000")
                && !value.substring(36, 52).equals("0000000000000000");
    }

    private String newTraceparent() {
        return "00-" + hexUuid(UUID.randomUUID()) + "-" + hexUuid(UUID.randomUUID()).substring(0, 16) + "-01";
    }

    private String hexUuid(UUID value) {
        return value.toString().replace("-", "");
    }
}
