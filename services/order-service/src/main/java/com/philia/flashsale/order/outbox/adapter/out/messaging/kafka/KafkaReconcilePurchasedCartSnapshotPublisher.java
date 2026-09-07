package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotV1;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.outbox.application.port.PublishOrderEventPort;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/** Relays the confirmed Cart cleanup command after the Order outbox commits. */
public final class KafkaReconcilePurchasedCartSnapshotPublisher implements PublishOrderEventPort {
    private final KafkaTemplate<String, ReconcilePurchasedCartSnapshotV1> kafka;
    private final ReconcilePurchasedCartSnapshotAvroMapper mapper;
    private final OrderKafkaProperties properties;

    public KafkaReconcilePurchasedCartSnapshotPublisher(
            KafkaTemplate<String, ReconcilePurchasedCartSnapshotV1> kafka,
            ReconcilePurchasedCartSnapshotAvroMapper mapper, OrderKafkaProperties properties) {
        this.kafka = Objects.requireNonNull(kafka, "kafka");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void publish(OrderOutboxEvent event) {
        var record = new ProducerRecord<String, ReconcilePurchasedCartSnapshotV1>(
                properties.cartReconciliationCommandsTopic(), event.eventKey(), mapper.map(event));
        if (event.traceparent() != null) record.headers().add("traceparent",
                event.traceparent().getBytes(StandardCharsets.UTF_8));
        if (event.tracestate() != null) record.headers().add("tracestate",
                event.tracestate().getBytes(StandardCharsets.UTF_8));
        try {
            kafka.send(record).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cart reconciliation publication interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Cart reconciliation publication failed", exception);
        }
    }
}
