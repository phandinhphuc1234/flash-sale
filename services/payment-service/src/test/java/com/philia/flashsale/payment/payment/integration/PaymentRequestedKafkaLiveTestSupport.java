package com.philia.flashsale.payment.payment.integration;

import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedDataV1;
import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Shared external Kafka/Schema Registry harness for the explicitly enabled G4 live suite. */
final class PaymentRequestedKafkaLiveTestSupport {

    static final String COMMAND_TOPIC = "flashsale.payment.commands.v1";
    static final String DLT_TOPIC = "flashsale.payment.payment-requested.dlt.v1";
    static final String TRACEPARENT = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";

    private PaymentRequestedKafkaLiveTestSupport() {
    }

    static String bootstrapServers() {
        return System.getProperty("payment.kafka.bootstrap", "localhost:29092");
    }

    static String schemaRegistryUrl() {
        return System.getProperty("payment.schema-registry.url", "http://localhost:8081");
    }

    static KafkaProducer<String, SpecificRecord> producer() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        properties.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl());
        properties.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, false);
        properties.put(AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY,
                "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new KafkaProducer<>(properties);
    }

    static RecordMetadata send(KafkaProducer<String, SpecificRecord> producer, String key,
            PaymentRequestedV1 event) throws Exception {
        ProducerRecord<String, SpecificRecord> record = new ProducerRecord<>(COMMAND_TOPIC, key, event);
        addHeader(record, "eventId", event.getEventId().toString());
        addHeader(record, "eventType", "PaymentRequested");
        addHeader(record, "eventVersion", "1");
        addHeader(record, "contentType", "application/avro");
        addHeader(record, "traceparent", TRACEPARENT);
        addHeader(record, "tracestate", "vendor=value");
        return producer.send(record).get(10, TimeUnit.SECONDS);
    }

    static ConsumerRecord<String, Object> consume(String topic,
            Predicate<ConsumerRecord<String, Object>> match) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-g4-probe-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        properties.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl());
        properties.put("specific.avro.reader", true);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, Object> record : consumer.poll(Duration.ofMillis(500))) {
                    if (match.test(record)) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("Timed out waiting for the expected Kafka record on " + topic);
    }

    static PaymentRequestedV1 event(UUID eventId, UUID orderId, UUID userId) {
        Instant occurredAt = Instant.now();
        return new PaymentRequestedV1(eventId, "PaymentRequested", 1, "order-service", "ORDER",
                orderId, 1L, UUID.randomUUID(), UUID.randomUUID(), occurredAt,
                new PaymentRequestedDataV1(orderId, userId, new BigDecimal("100000.0000"), "VND",
                        occurredAt.plus(Duration.ofMinutes(10))));
    }

    static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    static void await(String description, Duration timeout, CheckedBooleanSupplier condition)
            throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private static void addHeader(ProducerRecord<String, SpecificRecord> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }

    @FunctionalInterface
    interface CheckedBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }
}
