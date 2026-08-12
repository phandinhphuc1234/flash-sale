package com.philia.flashsale.flashsale.outbox.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1;
import com.philia.flashsale.flashsale.outbox.adapter.out.messaging.kafka.PurchaseAcceptedAvroMapper;
import com.philia.flashsale.flashsale.outbox.application.model.OutboxEvent;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.avro.Schema;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Opt-in black-box verification against the local Kafka + Confluent Registry stack.
 *
 * <p>Start {@code infra/docker/compose.yml} with Kafka and Schema Registry, then set
 * {@code RUN_KAFKA_INTEGRATION_TESTS=true}. The default Maven build intentionally skips this
 * environment-dependent test while still compiling it.</p>
 */
@EnabledIfEnvironmentVariable(named = "RUN_KAFKA_INTEGRATION_TESTS", matches = "true")
class PurchaseAcceptedKafkaIntegrationTests {
    private static final String TOPIC = "flashsale.purchase.events.v1";
    private static final String BOOTSTRAP = env("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092");
    private static final String REGISTRY = env("SCHEMA_REGISTRY_URL", "http://localhost:8081");
    private static final String RECORD_NAME = "com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1";
    private static final String SUBJECT = TOPIC + "-" + RECORD_NAME;

    private static KafkaProducer<String, PurchaseAcceptedV1> producer;
    private static KafkaConsumer<String, PurchaseAcceptedV1> consumer;

    @BeforeAll
    static void startClients() throws Exception {
        Schema schema = new PurchaseAcceptedV1().getSchema();
        assertEquals(RECORD_NAME, schema.getFullName(), "record full name");
        configureCompatibility();
        SchemaRegistryClient registry = new CachedSchemaRegistryClient(REGISTRY, 20);
        assertTrue(registry.register(SUBJECT, schema) > 0, "schema must be registered explicitly");
        createTopic();
        producer = new KafkaProducer<>(producerProperties(BOOTSTRAP, REGISTRY));
        consumer = new KafkaConsumer<>(consumerProperties(BOOTSTRAP, REGISTRY));
        consumer.subscribe(List.of(TOPIC));
        consumer.poll(Duration.ofSeconds(5));
    }

    @AfterAll
    static void stopClients() {
        if (producer != null) {
            producer.close(Duration.ofSeconds(5));
        }
        if (consumer != null) {
            consumer.close(Duration.ofSeconds(5));
        }
    }

    @Test
    void realSerializationDuplicateDeliveryAndEndpointRecoveryPreserveIdentity() throws Exception {
        PurchaseAcceptedAvroMapper mapper = new PurchaseAcceptedAvroMapper();
        OutboxEvent original = event(UUID.randomUUID());
        PurchaseAcceptedV1 value = mapper.map(original);
        String key = original.aggregateId().toString();

        producer.send(new ProducerRecord<>(TOPIC, key, value)).get(10, TimeUnit.SECONDS);
        producer.send(new ProducerRecord<>(TOPIC, key, value)).get(10, TimeUnit.SECONDS);
        List<ConsumerRecord<String, PurchaseAcceptedV1>> duplicates = consume(2);
        assertEquals(2, duplicates.size());
        assertEquals(key, duplicates.get(0).key());
        assertEquals(original.eventId(), duplicates.get(0).value().getEventId());
        assertEquals(original.eventId(), duplicates.get(1).value().getEventId());
        assertEquals(value.getOccurredAt(), duplicates.get(1).value().getOccurredAt());

        PurchaseAcceptedV1 retryValue = mapper.map(event(UUID.randomUUID()));
        try (KafkaProducer<String, PurchaseAcceptedV1> registryOutage =
                new KafkaProducer<>(producerProperties(BOOTSTRAP, "http://127.0.0.1:1"))) {
            assertThrows(Exception.class,
                    () -> registryOutage.send(new ProducerRecord<>(TOPIC, key, retryValue))
                            .get(3, TimeUnit.SECONDS));
        }
        try (KafkaProducer<String, PurchaseAcceptedV1> brokerOutage =
                new KafkaProducer<>(producerProperties("127.0.0.1:1", REGISTRY))) {
            assertThrows(Exception.class,
                    () -> brokerOutage.send(new ProducerRecord<>(TOPIC, key, retryValue))
                            .get(3, TimeUnit.SECONDS));
        }

        producer.send(new ProducerRecord<>(TOPIC, key, retryValue)).get(10, TimeUnit.SECONDS);
        ConsumerRecord<String, PurchaseAcceptedV1> recovered = consume(1).get(0);
        assertNotNull(recovered.value());
        assertEquals(retryValue.getEventId(), recovered.value().getEventId());
    }

    private static List<ConsumerRecord<String, PurchaseAcceptedV1>> consume(int expected) {
        List<ConsumerRecord<String, PurchaseAcceptedV1>> records = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (records.size() < expected && System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(500)).forEach(records::add);
        }
        assertEquals(expected, records.size(), "Kafka records did not arrive before timeout");
        return records;
    }

    private static void createTopic() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        } catch (ExecutionException exception) {
            if (!(exception.getCause() instanceof TopicExistsException)) {
                throw exception;
            }
        }
    }

    private static void configureCompatibility() throws Exception {
        String subject = java.net.URLEncoder.encode(SUBJECT, StandardCharsets.UTF_8)
                .replace("+", "%20");
        HttpRequest request = HttpRequest.newBuilder(URI.create(REGISTRY + "/config/" + subject))
                .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"compatibilityLevel\":\"BACKWARD_TRANSITIVE\"}"))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
    }

    private static Properties producerProperties(String bootstrap, String registry) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        properties.put("schema.registry.url", registry);
        properties.put("auto.register.schemas", false);
        properties.put("value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 1_500);
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 500);
        return properties;
    }

    private static Properties consumerProperties(String bootstrap, String registry) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "purchase-accepted-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        properties.put("schema.registry.url", registry);
        properties.put("specific.avro.reader", true);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return properties;
    }

    private static OutboxEvent event(UUID eventId) {
        Instant acceptedAt = Instant.parse("2026-08-12T10:00:00Z");
        Map<String, Object> payload = new HashMap<>();
        payload.put("purchaseRequestId", UUID.randomUUID().toString());
        payload.put("reservationId", UUID.randomUUID().toString());
        payload.put("campaignId", UUID.randomUUID().toString());
        payload.put("variantId", UUID.randomUUID().toString());
        payload.put("userId", UUID.randomUUID().toString());
        payload.put("quantity", 1L);
        payload.put("unitPrice", "99000.0000");
        payload.put("currency", "VND");
        payload.put("acceptedAt", acceptedAt.toString());
        payload.put("expiresAt", acceptedAt.plusSeconds(30).toString());
        return new OutboxEvent(eventId, "PURCHASE_REQUEST", UUID.randomUUID(), 1L,
                "PurchaseAccepted", 1, payload, "PENDING", 0, acceptedAt,
                null, null, null, null, acceptedAt, acceptedAt);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
