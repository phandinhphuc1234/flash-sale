package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.contract.order.event.v1.OrderCreatedV1;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.outbox.application.port.PublishOrderCreatedPort;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/** Publishes keyed OrderCreated facts and waits for broker acknowledgement. */
public final class KafkaOrderCreatedPublisher implements PublishOrderCreatedPort {
    private final KafkaTemplate<String, OrderCreatedV1> kafka;
    private final OrderCreatedAvroMapper mapper;
    private final OrderKafkaProperties properties;

    public KafkaOrderCreatedPublisher(KafkaTemplate<String, OrderCreatedV1> kafka,
            OrderCreatedAvroMapper mapper, OrderKafkaProperties properties) {
        this.kafka = Objects.requireNonNull(kafka, "kafka");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void publish(OrderOutboxEvent event) {
        OrderCreatedV1 value = mapper.map(event);
        ProducerRecord<String, OrderCreatedV1> record = new ProducerRecord<>(
                properties.orderEventsTopic(), event.eventKey(), value);
        addHeader(record, "traceparent", event.traceparent());
        addHeader(record, "tracestate", event.tracestate());
        try {
            kafka.send(record).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OrderCreated publication was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("OrderCreated publication failed", exception.getCause());
        }
    }

    private void addHeader(ProducerRecord<String, OrderCreatedV1> record, String name, String value) {
        if (value != null && !value.isBlank()) {
            record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
