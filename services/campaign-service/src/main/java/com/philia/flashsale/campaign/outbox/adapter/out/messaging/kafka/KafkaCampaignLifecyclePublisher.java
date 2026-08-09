package com.philia.flashsale.campaign.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.campaign.outbox.application.model.OutboxClaim;
import com.philia.flashsale.campaign.outbox.application.port.out.PublishCampaignOutboxEventPort;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes canonical Campaign lifecycle SpecificRecords from the durable outbox. */
@Component
public class KafkaCampaignLifecyclePublisher implements PublishCampaignOutboxEventPort {

    static final String TOPIC = "campaign.lifecycle.v1";
    static final String CONTENT_TYPE = "application/avro";

    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    private final CampaignLifecycleAvroMapper avroMapper;

    public KafkaCampaignLifecyclePublisher(
            @Qualifier("campaignKafkaTemplate") KafkaTemplate<String, SpecificRecord> kafkaTemplate,
            CampaignLifecycleAvroMapper avroMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.avroMapper = avroMapper;
    }

    @Override
    public void publish(OutboxClaim event) {
        Objects.requireNonNull(event, "Outbox claim is required");
        kafkaTemplate.send(toProducerRecord(event)).join();
    }

    ProducerRecord<String, SpecificRecord> toProducerRecord(OutboxClaim event) {
        if (!event.aggregateId().toString().equals(event.eventKey())) {
            throw new IllegalArgumentException("Campaign outbox key must equal aggregate ID");
        }
        SpecificRecord record = avroMapper.toRecord(event);
        ProducerRecord<String, SpecificRecord> producerRecord =
                new ProducerRecord<>(TOPIC, event.aggregateId().toString(), record);
        addHeader(producerRecord, "eventId", event.id().toString());
        addHeader(producerRecord, "eventType", event.eventType());
        addHeader(producerRecord, "eventVersion", Integer.toString(event.eventVersion()));
        addHeader(producerRecord, "contentType", CONTENT_TYPE);
        producerRecord.headers().add("traceparent", W3CTraceContextHeaders.traceparent(event.traceId()));
        return producerRecord;
    }

    private void addHeader(ProducerRecord<String, SpecificRecord> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
