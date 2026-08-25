package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.contract.order.event.v1.OrderExpiredV1;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.outbox.application.port.PublishOrderEventPort;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/** Publishes durable OrderExpired facts after the outbox transaction commits. */
public final class KafkaOrderExpiredPublisher implements PublishOrderEventPort {
    private final KafkaTemplate<String, OrderExpiredV1> kafka; private final OrderExpiredAvroMapper mapper; private final OrderKafkaProperties properties;
    public KafkaOrderExpiredPublisher(KafkaTemplate<String, OrderExpiredV1> kafka, OrderExpiredAvroMapper mapper, OrderKafkaProperties properties) { this.kafka = kafka; this.mapper = mapper; this.properties = properties; }
    @Override public void publish(OrderOutboxEvent event) { var value = mapper.map(event); var record = new ProducerRecord<String, OrderExpiredV1>(properties.orderEventsTopic(), event.eventKey(), value); header(record, "traceparent", event.traceparent()); header(record, "tracestate", event.tracestate()); try { kafka.send(record).get(30, TimeUnit.SECONDS); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("OrderExpired publication interrupted", exception); } catch (Exception exception) { throw new IllegalStateException("OrderExpired publication failed", exception); } }
    private void header(ProducerRecord<?, ?> record, String name, String value) { if (value != null && !value.isBlank()) record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)); }
}
