package com.philia.flashsale.payment.outbox.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.contract.payment.event.v1.PaymentFailedDataV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentFailedV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentSucceededDataV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentSucceededV1;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Live broker/Registry evidence. Enable explicitly with
 * {@code -Dpayment.kafka.integration=true}; ordinary CI does not require external infrastructure.
 */
@EnabledIfSystemProperty(named = "payment.kafka.integration", matches = "true")
class PaymentResultKafkaIntegrationTests {

    private static final String TOPIC = "flashsale.payment.events.v1";
    private static final Instant EVENT_TIME = Instant.parse("2030-08-01T10:00:00Z");

    @Test
    void publishesBothResultTypesWithOrderKeyHeadersAndPhysicalDuplicateIdentity() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID successId = UUID.randomUUID();
        UUID failureId = UUID.randomUUID();
        PaymentSucceededV1 success = success(successId, paymentId, orderId);
        PaymentFailedV1 failure = failure(failureId, paymentId, orderId);

        try (KafkaProducer<String, SpecificRecord> producer = new KafkaProducer<>(producerProperties())) {
            producer.send(record(orderId, success, successId)).get(10, TimeUnit.SECONDS);
            producer.send(record(orderId, failure, failureId)).get(10, TimeUnit.SECONDS);
            // A broker redelivery preserves the same eventId and is safe for downstream deduplication.
            producer.send(record(orderId, success, successId)).get(10, TimeUnit.SECONDS);
        }

        List<ConsumerRecord<String, Object>> records = consume(orderId, successId, failureId);
        ConsumerRecord<String, Object> successRecord = records.stream()
                .filter(record -> record.value() instanceof PaymentSucceededV1 event
                        && successId.equals(event.getEventId()))
                .findFirst().orElseThrow();
        ConsumerRecord<String, Object> failureRecord = records.stream()
                .filter(record -> record.value() instanceof PaymentFailedV1 event
                        && failureId.equals(event.getEventId()))
                .findFirst().orElseThrow();

        assertThat(successRecord.key()).isEqualTo(orderId.toString());
        assertThat(failureRecord.key()).isEqualTo(orderId.toString());
        assertThat(header(successRecord, "eventId")).isEqualTo(successId.toString());
        assertThat(header(failureRecord, "eventType")).isEqualTo("PaymentFailed");
        assertThat(header(successRecord, "traceparent"))
                .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        assertThat(records.stream().filter(record -> record.value() instanceof PaymentSucceededV1 event
                && successId.equals(event.getEventId())).count()).isEqualTo(2);
        assertThat(registryCompatibility("PaymentSucceededV1")).contains("BACKWARD_TRANSITIVE");
    }

    @Test
    void failedRegistryAttemptCanRetryTheSamePhysicalIdentityAfterRecovery() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        PaymentSucceededV1 event = success(eventId, paymentId, orderId);

        Properties unavailableRegistry = producerProperties();
        unavailableRegistry.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                "http://127.0.0.1:1");
        unavailableRegistry.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2_000);
        assertThatThrownBy(() -> {
            try (KafkaProducer<String, SpecificRecord> producer = new KafkaProducer<>(unavailableRegistry)) {
                producer.send(record(orderId, event, eventId)).get(5, TimeUnit.SECONDS);
            }
        }).isInstanceOf(Exception.class);

        try (KafkaProducer<String, SpecificRecord> producer = new KafkaProducer<>(producerProperties())) {
            producer.send(record(orderId, event, eventId)).get(10, TimeUnit.SECONDS);
        }
        List<ConsumerRecord<String, Object>> records = consume(orderId, eventId, null);
        assertThat(records.stream().filter(record -> record.value() instanceof PaymentSucceededV1 value
                && eventId.equals(value.getEventId())).count()).isGreaterThanOrEqualTo(1);
    }

    private List<ConsumerRecord<String, Object>> consume(UUID orderId, UUID successId, UUID failureId) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-g7-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        properties.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl());
        properties.put("specific.avro.reader", true);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        List<ConsumerRecord<String, Object>> result = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC));
            while (System.nanoTime() < deadline && !containsAll(result, orderId, successId, failureId)) {
                consumer.poll(Duration.ofMillis(500)).forEach(result::add);
            }
        }
        return result;
    }

    private boolean containsAll(List<ConsumerRecord<String, Object>> records, UUID orderId,
            UUID successId, UUID failureId) {
        boolean hasOrder = records.stream().anyMatch(record -> orderId.toString().equals(record.key()));
        boolean hasSuccess = successId != null && records.stream().anyMatch(record ->
                record.value() instanceof PaymentSucceededV1 event && successId.equals(event.getEventId()));
        boolean hasFailure = failureId == null || records.stream().anyMatch(record ->
                record.value() instanceof PaymentFailedV1 event && failureId.equals(event.getEventId()));
        return hasOrder && hasSuccess && hasFailure;
    }

    private Properties producerProperties() {
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
        return properties;
    }

    private ProducerRecord<String, SpecificRecord> record(UUID orderId, SpecificRecord value, UUID eventId) {
        ProducerRecord<String, SpecificRecord> record = new ProducerRecord<>(TOPIC, orderId.toString(), value);
        record.headers().add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", value instanceof PaymentSucceededV1
                ? "PaymentSucceeded".getBytes(StandardCharsets.UTF_8)
                : "PaymentFailed".getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventVersion", "1".getBytes(StandardCharsets.UTF_8));
        record.headers().add("contentType", "application/avro".getBytes(StandardCharsets.UTF_8));
        record.headers().add("traceparent", ("00-" + eventId.toString().replace("-", "")
                + "-0000000000000001-01").getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private String header(ConsumerRecord<String, Object> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private String registryCompatibility(String recordName) throws Exception {
        String subject = TOPIC + "-com.philia.flashsale.contract.payment.event.v1." + recordName;
        HttpRequest request = HttpRequest.newBuilder(URI.create(schemaRegistryUrl() + "/config/"
                + URLEncoder.encode(subject, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private String bootstrapServers() {
        return System.getProperty("payment.kafka.bootstrap", "localhost:29092");
    }

    private String schemaRegistryUrl() {
        return System.getProperty("payment.schema-registry.url", "http://localhost:8081");
    }

    private PaymentSucceededV1 success(UUID eventId, UUID paymentId, UUID orderId) {
        return new PaymentSucceededV1(eventId, "PaymentSucceeded", 1, "payment-service", "PAYMENT", paymentId,
                3L, orderId, UUID.randomUUID(), EVENT_TIME,
                new PaymentSucceededDataV1(paymentId, orderId, new BigDecimal("125.5000"), "VND", EVENT_TIME,
                        "STRIPE", "cs_test_g7", null));
    }

    private PaymentFailedV1 failure(UUID eventId, UUID paymentId, UUID orderId) {
        return new PaymentFailedV1(eventId, "PaymentFailed", 1, "payment-service", "PAYMENT", paymentId,
                4L, orderId, UUID.randomUUID(), EVENT_TIME,
                new PaymentFailedDataV1(paymentId, orderId, new BigDecimal("125.5000"), "VND", EVENT_TIME,
                        "PAYMENT_DEADLINE_EXPIRED", "STRIPE", null));
    }
}
