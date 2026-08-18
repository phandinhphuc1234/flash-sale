package com.philia.flashsale.payment.payment.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestResult;
import com.philia.flashsale.payment.payment.application.port.in.AcceptPaymentRequestUseCase;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Inbound Kafka adapter that acknowledges only after the Payment transaction has returned. */
@Component
@ConditionalOnProperty(name = "payment.kafka.consumer-enabled", havingValue = "true")
@ConditionalOnBean(AcceptPaymentRequestUseCase.class)
public final class PaymentRequestedKafkaConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentRequestedKafkaConsumer.class);
    private static final String TRACEPARENT = "traceparent";
    private static final String TRACESTATE = "tracestate";
    private static final String TRACE_ID = "traceId";

    private final PaymentRequestedAvroMapper mapper;
    private final AcceptPaymentRequestUseCase useCase;

    @Autowired
    public PaymentRequestedKafkaConsumer(PaymentRequestedAvroMapper mapper,
            AcceptPaymentRequestUseCase useCase) {
        this.mapper = mapper;
        this.useCase = useCase;
    }

    @KafkaListener(topics = "${payment.kafka.command-topic}",
            groupId = "${payment.kafka.consumer-group}",
            containerFactory = "paymentRequestedKafkaListenerContainerFactory",
            autoStartup = "${payment.kafka.consumer-enabled:false}")
    public void onMessage(ConsumerRecord<String, PaymentRequestedV1> record,
            Acknowledgment acknowledgment) {
        String incomingTraceparent = header(record, TRACEPARENT);
        String effectiveTraceparent = isValidTraceparent(incomingTraceparent)
                ? incomingTraceparent
                : newTraceparent();
        try (MDC.MDCCloseable trace = MDC.putCloseable(TRACE_ID, effectiveTraceparent.substring(3, 35));
                MDC.MDCCloseable parent = MDC.putCloseable(TRACEPARENT, effectiveTraceparent);
                MDC.MDCCloseable state = MDC.putCloseable(TRACESTATE,
                        header(record, TRACESTATE) == null ? "" : header(record, TRACESTATE))) {
            consume(record, acknowledgment);
        } catch (PaymentRequestedRecordException | PaymentRequestedConflictException exception) {
            LOGGER.warn("Rejected PaymentRequested record eventId={} key={} reason={}",
                    safeEventId(record), record == null ? null : record.key(), exception.getMessage());
            throw exception;
        }
    }

    private void consume(ConsumerRecord<String, PaymentRequestedV1> record,
            Acknowledgment acknowledgment) {
        var result = useCase.accept(mapper.map(record));
        if (result.outcome() == AcceptPaymentRequestResult.Outcome.CONFLICT) {
            throw new PaymentRequestedConflictException("Payment identity conflict");
        }
        if (result.outcome() != AcceptPaymentRequestResult.Outcome.ACCEPTED
                && result.outcome() != AcceptPaymentRequestResult.Outcome.EXPIRED
                && result.outcome() != AcceptPaymentRequestResult.Outcome.EVENT_REPLAYED
                && result.outcome() != AcceptPaymentRequestResult.Outcome.BUSINESS_REPLAYED) {
            throw new IllegalStateException("unsupported Payment command outcome");
        }
        // The use case returns only after the local transaction has committed.
        acknowledgment.acknowledge();
    }

    private String safeEventId(ConsumerRecord<String, PaymentRequestedV1> record) {
        if (record == null || record.value() == null || record.value().getEventId() == null) {
            return "unknown";
        }
        return record.value().getEventId().toString();
    }

    private String header(ConsumerRecord<String, PaymentRequestedV1> record, String name) {
        if (record == null) {
            return null;
        }
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    static boolean isValidTraceparent(String value) {
        return value != null && value.matches("[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")
                && !value.substring(5, 37).matches("0{32}")
                && !value.substring(38, 54).matches("0{16}");
    }

    private String newTraceparent() {
        return "00-" + UUID.randomUUID().toString().replace("-", "") + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "-01";
    }
}
