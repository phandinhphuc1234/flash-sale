package com.philia.flashsale.flashsale.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationReleasedV1;
import com.philia.flashsale.flashsale.configuration.OutboxProperties;
import com.philia.flashsale.flashsale.outbox.application.model.OutboxEvent;
import com.philia.flashsale.flashsale.outbox.application.port.PublishPurchaseReservationReleasedPort;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Publishes durable reservation release facts keyed by Order identity. */
@Component
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public final class KafkaPurchaseReservationReleasedPublisher implements PublishPurchaseReservationReleasedPort {
    private final KafkaTemplate<String, PurchaseReservationReleasedV1> kafka;
    private final PurchaseReservationReleasedAvroMapper mapper;
    private final OutboxProperties properties;

    public KafkaPurchaseReservationReleasedPublisher(KafkaTemplate<String, PurchaseReservationReleasedV1> kafka,
            PurchaseReservationReleasedAvroMapper mapper, OutboxProperties properties) {
        this.kafka = Objects.requireNonNull(kafka, "kafka");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void publish(OutboxEvent event) {
        var value = mapper.map(event);
        var record = new ProducerRecord<String, PurchaseReservationReleasedV1>(properties.topic(),
                value.getData().getOrderId().toString(), value);
        header(record, "traceparent", event.payload().get("traceparent"));
        header(record, "tracestate", event.payload().get("tracestate"));
        try {
            kafka.send(record).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("released outcome publication interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("released outcome publication failed", exception);
        }
    }

    private void header(ProducerRecord<?, ?> record, String name, Object value) {
        if (value != null && !value.toString().isBlank()) {
            record.headers().add(name, value.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
