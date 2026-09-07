package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.contract.order.event.v2.OrderCancelledV2;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.outbox.application.port.PublishOrderEventPort;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/** Publishes additive regular OrderCancelled V2 facts. */
public final class KafkaOrderCancelledV2Publisher implements PublishOrderEventPort {
    private final KafkaTemplate<String, OrderCancelledV2> kafka; private final OrderCancelledV2AvroMapper mapper; private final OrderKafkaProperties properties;
    public KafkaOrderCancelledV2Publisher(KafkaTemplate<String, OrderCancelledV2> kafka, OrderCancelledV2AvroMapper mapper, OrderKafkaProperties properties) { this.kafka = kafka; this.mapper = mapper; this.properties = properties; }
    @Override public void publish(OrderOutboxEvent event) { var record = new ProducerRecord<String, OrderCancelledV2>(properties.orderEventsTopic(), event.eventKey(), mapper.map(event)); header(record, "traceparent", event.traceparent()); header(record, "tracestate", event.tracestate()); try { kafka.send(record).get(30, TimeUnit.SECONDS); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("OrderCancelledV2 publication interrupted", exception); } catch (Exception exception) { throw new IllegalStateException("OrderCancelledV2 publication failed", exception); } }
    private void header(ProducerRecord<?, ?> record, String name, String value) { if (value != null && !value.isBlank()) record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)); }
}
