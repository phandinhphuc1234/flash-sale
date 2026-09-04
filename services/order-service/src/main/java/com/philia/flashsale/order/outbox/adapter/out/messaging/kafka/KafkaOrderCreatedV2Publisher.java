package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.contract.order.event.v2.OrderCreatedV2;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.outbox.application.port.PublishOrderEventPort;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/** Publishes additive regular OrderCreated V2 facts without altering Flash Sale V1 publication. */
public final class KafkaOrderCreatedV2Publisher implements PublishOrderEventPort {
    private final KafkaTemplate<String, OrderCreatedV2> kafka;
    private final OrderCreatedV2AvroMapper mapper;
    private final OrderKafkaProperties properties;

    public KafkaOrderCreatedV2Publisher(KafkaTemplate<String, OrderCreatedV2> kafka,
            OrderCreatedV2AvroMapper mapper, OrderKafkaProperties properties) {
        this.kafka = Objects.requireNonNull(kafka, "kafka");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void publish(OrderOutboxEvent event) {
        var record = new ProducerRecord<String, OrderCreatedV2>(properties.orderEventsTopic(), event.eventKey(),
                mapper.map(event));
        header(record, "traceparent", event.traceparent());
        header(record, "tracestate", event.tracestate());
        try {
            kafka.send(record).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OrderCreatedV2 publication interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("OrderCreatedV2 publication failed", exception);
        }
    }

    private void header(ProducerRecord<?, ?> record, String name, String value) {
        if (value != null && !value.isBlank()) record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
