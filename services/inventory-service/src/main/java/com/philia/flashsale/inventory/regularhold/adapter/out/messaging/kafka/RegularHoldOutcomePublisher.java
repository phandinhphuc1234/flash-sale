package com.philia.flashsale.inventory.regularhold.adapter.out.messaging.kafka;

import com.philia.flashsale.inventory.configuration.InventoryRegularHoldProperties;
import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldOutboxEvent;
import java.nio.charset.StandardCharsets;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Kafka adapter for durable regular-hold results; the scheduler owns retry and lease handling. */
@Component
@ConditionalOnProperty(name = "flashsale.inventory.regular-hold.outbox-publisher-enabled", havingValue = "true")
public class RegularHoldOutcomePublisher {
    private final KafkaTemplate<String, SpecificRecord> kafka;
    private final RegularHoldOutcomeAvroMapper mapper;
    private final InventoryRegularHoldProperties properties;

    public RegularHoldOutcomePublisher(KafkaTemplate<String, SpecificRecord> kafka, RegularHoldOutcomeAvroMapper mapper,
            InventoryRegularHoldProperties properties) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.properties = properties;
    }

    public void publish(RegularHoldOutboxEvent outbox) {
        SpecificRecord value = mapper.toRecord(outbox);
        ProducerRecord<String, SpecificRecord> record = new ProducerRecord<>(properties.getEventsTopic(),
                outbox.eventKey(), value);
        record.headers().add("eventId", outbox.eventId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", outbox.eventType().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventVersion", "1".getBytes(StandardCharsets.UTF_8));
        if (outbox.traceparent() != null) {
            record.headers().add("traceparent", outbox.traceparent().getBytes(StandardCharsets.UTF_8));
        }
        if (outbox.tracestate() != null) {
            record.headers().add("tracestate", outbox.tracestate().getBytes(StandardCharsets.UTF_8));
        }
        kafka.send(record).join();
    }
}
