package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.contract.order.event.v1.OrderConfirmedV1;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.outbox.application.port.PublishOrderEventPort;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/** Publishes durable OrderConfirmed facts after the outbox transaction commits. */
public final class KafkaOrderConfirmedPublisher implements PublishOrderEventPort {
    private final KafkaTemplate<String, OrderConfirmedV1> kafka;
    private final OrderConfirmedAvroMapper mapper;
    private final OrderKafkaProperties properties;

    public KafkaOrderConfirmedPublisher(KafkaTemplate<String, OrderConfirmedV1> kafka,
            OrderConfirmedAvroMapper mapper, OrderKafkaProperties properties) {
        this.kafka = Objects.requireNonNull(kafka, "kafka");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void publish(OrderOutboxEvent event) {
        var record = new ProducerRecord<String, OrderConfirmedV1>(properties.orderEventsTopic(),
                event.eventKey(), mapper.map(event));
        addHeader(record, "traceparent", event.traceparent());
        addHeader(record, "tracestate", event.tracestate());
        try {
            kafka.send(record).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OrderConfirmed publication was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("OrderConfirmed publication failed", exception);
        }
    }

    private void addHeader(ProducerRecord<?, ?> record, String name, String value) {
        if (value != null && !value.isBlank()) {
            record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
