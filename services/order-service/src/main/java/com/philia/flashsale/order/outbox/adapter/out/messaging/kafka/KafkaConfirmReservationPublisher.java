package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationV1;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.outbox.application.port.PublishOrderEventPort;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/** Publishes reservation confirmation commands after durable outbox leasing. */
public final class KafkaConfirmReservationPublisher implements PublishOrderEventPort {
    private final KafkaTemplate<String, ConfirmPurchaseReservationV1> kafka;
    private final ConfirmReservationAvroMapper mapper;
    private final OrderKafkaProperties properties;

    public KafkaConfirmReservationPublisher(KafkaTemplate<String, ConfirmPurchaseReservationV1> kafka,
            ConfirmReservationAvroMapper mapper, OrderKafkaProperties properties) {
        this.kafka = kafka; this.mapper = mapper; this.properties = properties;
    }

    @Override
    public void publish(OrderOutboxEvent event) {
        var value = mapper.map(event);
        var record = new ProducerRecord<String, ConfirmPurchaseReservationV1>(
                properties.purchaseCommandsTopic(), event.eventKey(), value);
        header(record, "traceparent", event.traceparent());
        header(record, "tracestate", event.tracestate());
        try { kafka.send(record).get(30, TimeUnit.SECONDS); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("confirm publication interrupted", exception); }
        catch (Exception exception) { throw new IllegalStateException("confirm publication failed", exception); }
    }

    private void header(ProducerRecord<?, ?> record, String name, String value) {
        if (value != null && !value.isBlank()) record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
