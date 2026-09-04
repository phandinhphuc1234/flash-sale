package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.contract.regularhold.command.v1.ConfirmRegularStockHoldV1;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.outbox.application.port.PublishOrderEventPort;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/** Relays one durable regular-hold confirm command to Inventory using the Order key. */
public final class KafkaConfirmRegularStockHoldPublisher implements PublishOrderEventPort {
    private final KafkaTemplate<String, ConfirmRegularStockHoldV1> kafka;
    private final ConfirmRegularStockHoldAvroMapper mapper;
    private final OrderKafkaProperties properties;

    public KafkaConfirmRegularStockHoldPublisher(KafkaTemplate<String, ConfirmRegularStockHoldV1> kafka,
            ConfirmRegularStockHoldAvroMapper mapper, OrderKafkaProperties properties) {
        this.kafka = Objects.requireNonNull(kafka, "kafka");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void publish(OrderOutboxEvent event) {
        var record = new ProducerRecord<String, ConfirmRegularStockHoldV1>(properties.regularHoldCommandsTopic(),
                event.eventKey(), mapper.map(event));
        header(record, "traceparent", event.traceparent());
        header(record, "tracestate", event.tracestate());
        try {
            kafka.send(record).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("regular hold confirm publication interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("regular hold confirm publication failed", exception);
        }
    }

    private void header(ProducerRecord<?, ?> record, String name, String value) {
        if (value != null && !value.isBlank()) record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
