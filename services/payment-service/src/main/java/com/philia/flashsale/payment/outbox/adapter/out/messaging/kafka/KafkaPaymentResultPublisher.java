package com.philia.flashsale.payment.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.payment.outbox.application.model.PaymentOutboxEvent;
import com.philia.flashsale.payment.outbox.application.port.PublishPaymentOutboxPort;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;

/** Publishes exact Payment result SpecificRecords with order-key and W3C header guarantees. */
public final class KafkaPaymentResultPublisher implements PublishPaymentOutboxPort {

    static final String CONTENT_TYPE = "application/avro";

    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    private final PaymentResultAvroMapper avroMapper;
    private final String eventTopic;

    public KafkaPaymentResultPublisher(
            @Qualifier("paymentKafkaTemplate") KafkaTemplate<String, SpecificRecord> kafkaTemplate,
            PaymentResultAvroMapper avroMapper, String eventTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.avroMapper = Objects.requireNonNull(avroMapper, "avroMapper");
        if (eventTopic == null || eventTopic.isBlank()) {
            throw new IllegalArgumentException("payment event topic must not be blank");
        }
        this.eventTopic = eventTopic;
    }

    @Override
    public void publish(PaymentOutboxEvent event) {
        Objects.requireNonNull(event, "outbox event is required");
        kafkaTemplate.send(toProducerRecord(event)).join();
    }

    ProducerRecord<String, SpecificRecord> toProducerRecord(PaymentOutboxEvent event) {
        if (!eventTopic.equals(event.topicName())) {
            throw new IllegalArgumentException("Payment outbox topic does not match configured event topic");
        }
        SpecificRecord record = avroMapper.toRecord(event);
        ProducerRecord<String, SpecificRecord> producerRecord =
                new ProducerRecord<>(eventTopic, event.messageKey().toString(), record);
        addHeader(producerRecord, "eventId", event.eventId().toString());
        addHeader(producerRecord, "eventType", event.eventType());
        addHeader(producerRecord, "eventVersion", Integer.toString(event.eventVersion()));
        addHeader(producerRecord, "contentType", CONTENT_TYPE);
        producerRecord.headers().add("traceparent", PaymentTraceContextHeaders.traceparent(
                event.traceparent(), event.eventId(), event.aggregateId()));
        if (event.tracestate() != null && !event.tracestate().isBlank()) {
            addHeader(producerRecord, "tracestate", event.tracestate());
        }
        return producerRecord;
    }

    private void addHeader(ProducerRecord<String, SpecificRecord> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
