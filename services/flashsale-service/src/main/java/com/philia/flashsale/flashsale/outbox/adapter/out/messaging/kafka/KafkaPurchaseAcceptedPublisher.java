package com.philia.flashsale.flashsale.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1;
import com.philia.flashsale.flashsale.configuration.OutboxProperties;
import com.philia.flashsale.flashsale.outbox.application.model.OutboxEvent;
import com.philia.flashsale.flashsale.outbox.application.port.PublishPurchaseAcceptedPort;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes keyed Avro events and waits for the broker acknowledgement outside PostgreSQL. */
@Component
@ConditionalOnBean(KafkaTemplate.class)
public final class KafkaPurchaseAcceptedPublisher implements PublishPurchaseAcceptedPort {
    private final KafkaTemplate<String, PurchaseAcceptedV1> kafka;
    private final PurchaseAcceptedAvroMapper mapper;
    private final OutboxProperties properties;

    public KafkaPurchaseAcceptedPublisher(KafkaTemplate<String, PurchaseAcceptedV1> kafka,
            PurchaseAcceptedAvroMapper mapper, OutboxProperties properties) {
        this.kafka = Objects.requireNonNull(kafka, "kafka");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void publish(OutboxEvent event) {
        PurchaseAcceptedV1 value = mapper.map(event);
        ProducerRecord<String, PurchaseAcceptedV1> record = new ProducerRecord<>(
                properties.topic(), event.aggregateId().toString(), value);
        addHeader(record, "traceparent", event.payload().get("traceparent"));
        addHeader(record, "tracestate", event.payload().get("tracestate"));
        try {
            kafka.send(record).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publication was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Kafka publication failed", exception.getCause());
        }
    }

    private void addHeader(ProducerRecord<String, PurchaseAcceptedV1> record, String name, Object value) {
        if (value != null && !value.toString().isBlank()) {
            record.headers().add(name, value.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
