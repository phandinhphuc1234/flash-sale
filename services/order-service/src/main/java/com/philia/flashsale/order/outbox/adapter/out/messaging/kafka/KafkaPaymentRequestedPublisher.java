package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.outbox.application.port.PublishOrderEventPort;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/** Publishes one stable PaymentRequested command keyed by orderId. */
public final class KafkaPaymentRequestedPublisher implements PublishOrderEventPort {
    private final KafkaTemplate<String, PaymentRequestedV1> kafka;
    private final PaymentRequestedAvroMapper mapper;
    private final OrderKafkaProperties properties;

    public KafkaPaymentRequestedPublisher(KafkaTemplate<String, PaymentRequestedV1> kafka,
            PaymentRequestedAvroMapper mapper, OrderKafkaProperties properties) {
        this.kafka = Objects.requireNonNull(kafka, "kafka");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void publish(OrderOutboxEvent event) {
        PaymentRequestedV1 value = mapper.map(event);
        ProducerRecord<String, PaymentRequestedV1> record = new ProducerRecord<>(
                properties.paymentCommandsTopic(), event.eventKey(), value);
        addHeader(record, "traceparent", event.traceparent());
        addHeader(record, "tracestate", event.tracestate());
        try {
            kafka.send(record).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PaymentRequested publication was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("PaymentRequested publication failed", exception.getCause());
        }
    }

    private void addHeader(ProducerRecord<String, PaymentRequestedV1> record, String name, String value) {
        if (value != null && !value.isBlank()) {
            record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
