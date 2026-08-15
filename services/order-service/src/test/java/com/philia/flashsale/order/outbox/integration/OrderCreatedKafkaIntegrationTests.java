package com.philia.flashsale.order.outbox.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.order.event.v1.OrderCreatedV1;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.KafkaOrderCreatedPublisher;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.OrderCreatedAvroMapper;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.support.KafkaIntegrationTestSupport;
import com.philia.flashsale.order.support.OrderIntegrationTestCondition;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

/** Opt-in live proof of Registry compatibility, keyed publication, headers, and duplicate identity. */
@ExtendWith(OrderIntegrationTestCondition.class)
@SpringBootTest(properties = {
        "order.creation.enabled=false",
        "order.runtime.accepted-purchase-consumer-enabled=false",
        "order.runtime.outbox-publisher-enabled=false",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.producer.properties.auto.register.schemas=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration"
})
class OrderCreatedKafkaIntegrationTests extends KafkaIntegrationTestSupport {
    private static final String TOPIC = "flashsale.order.events.v1";
    private static final String SUBJECT = TOPIC + "-com.philia.flashsale.contract.order.event.v1.OrderCreatedV1";
    private static final Instant CREATED = Instant.parse("2030-01-01T10:00:00Z");

    @Autowired
    private KafkaTemplate<String, OrderCreatedV1> kafkaTemplate;

    @Autowired
    private KafkaProperties kafkaProperties;

    @Autowired
    private OrderKafkaProperties orderKafkaProperties;

    @BeforeAll
    static void registerControlledOrderCreatedSubject() throws Exception {
        String registryUrl = schemaRegistryUrl();
        var registry = new CachedSchemaRegistryClient(registryUrl, 20);
        registry.register(SUBJECT, OrderCreatedV1.getClassSchema());
        registry.updateCompatibility(SUBJECT, "BACKWARD_TRANSITIVE");
    }

    @Test
    void publishesRegisteredAvroRecordWithOrderKeyAndTraceHeaders() throws Exception {
        OrderOutboxEvent event = event(UUID.randomUUID());
        new KafkaOrderCreatedPublisher(kafkaTemplate, new OrderCreatedAvroMapper(new ObjectMapper()),
                orderKafkaProperties).publish(event);

        ConsumerRecord<String, OrderCreatedV1> record = pollByEventId(event.eventId(), 1);
        assertThat(record.key()).isEqualTo(event.eventKey());
        assertThat(record.value().getEventId()).isEqualTo(event.eventId());
        assertThat(record.value().getData().getOrderId()).isEqualTo(event.aggregateId());
        assertThat(header(record, "traceparent")).isEqualTo(event.traceparent());
        assertThat(header(record, "tracestate")).isEqualTo(event.tracestate());
    }

    @Test
    void duplicatePhysicalPublicationKeepsTheSameEventIdentityAndPayload() throws Exception {
        OrderOutboxEvent event = event(UUID.randomUUID());
        var publisher = new KafkaOrderCreatedPublisher(kafkaTemplate, new OrderCreatedAvroMapper(new ObjectMapper()),
                orderKafkaProperties);

        publisher.publish(event);
        publisher.publish(event);

        List<ConsumerRecord<String, OrderCreatedV1>> records = pollByEventId(event.eventId(), 2, 15);
        assertThat(records).hasSize(2);
        assertThat(records).allSatisfy(record -> {
            assertThat(record.key()).isEqualTo(event.eventKey());
            assertThat(record.value().getEventId()).isEqualTo(event.eventId());
            assertThat(record.value().getData()).isEqualTo(records.get(0).value().getData());
        });
    }

    private ConsumerRecord<String, OrderCreatedV1> pollByEventId(UUID eventId, int count) {
        return pollByEventId(eventId, count, 10).get(0);
    }

    private List<ConsumerRecord<String, OrderCreatedV1>> pollByEventId(UUID eventId, int count, int timeoutSeconds) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "order-g5-publisher-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        properties.put("specific.avro.reader", true);
        properties.put("schema.registry.url", schemaRegistryUrl());
        var registry = new CachedSchemaRegistryClient(schemaRegistryUrl(), 20);
        var deserializer = new KafkaAvroDeserializer(registry,
                Map.of("schema.registry.url", schemaRegistryUrl(), "specific.avro.reader", true));
        @SuppressWarnings({"rawtypes", "unchecked"})
        KafkaConsumer<String, OrderCreatedV1> consumer = new KafkaConsumer(properties,
                new StringDeserializer(), deserializer);
        List<ConsumerRecord<String, OrderCreatedV1>> matches = new ArrayList<>();
        try (consumer) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
            while (System.nanoTime() < deadline && matches.size() < count) {
                for (ConsumerRecord<String, OrderCreatedV1> record : consumer.poll(Duration.ofMillis(250))) {
                    if (record.value() != null && eventId.equals(record.value().getEventId())) {
                        matches.add(record);
                    }
                }
            }
        }
        return matches;
    }

    private String header(ConsumerRecord<String, OrderCreatedV1> record, String name) {
        var value = record.headers().lastHeader(name);
        return value == null ? null : new String(value.value(), StandardCharsets.UTF_8);
    }

    private static String schemaRegistryUrl() {
        return System.getProperty("SCHEMA_REGISTRY_URL", "http://localhost:8081");
    }

    private static OrderOutboxEvent event(UUID orderId) {
        UUID purchaseRequestId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        String payload = "{"
                + "\"orderId\":\"" + orderId + "\",\"orderNumber\":\"ORD-" + orderId.toString().substring(0, 8) + "\","
                + "\"purchaseRequestId\":\"" + purchaseRequestId + "\",\"reservationId\":\"" + reservationId + "\","
                + "\"campaignId\":\"" + campaignId + "\",\"userId\":\"" + userId + "\","
                + "\"status\":\"PENDING_PAYMENT\",\"currency\":\"VND\","
                + "\"subtotalAmount\":\"20.0000\",\"totalAmount\":\"20.0000\","
                + "\"acceptedAt\":\"" + CREATED + "\",\"reservationExpiresAt\":\"" + CREATED.plusSeconds(300) + "\","
                + "\"items\":[{\"variantId\":\"" + variantId + "\",\"quantity\":2,"
                + "\"unitPrice\":\"10.0000\",\"lineAmount\":\"20.0000\"}]}";
        return new OrderOutboxEvent(UUID.randomUUID(), "ORDER", orderId, 1, "OrderCreated", 1,
                orderId.toString(), UUID.randomUUID(), UUID.randomUUID(), payload,
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01", "vendor=value", "IN_PROGRESS", 1,
                CREATED, "worker", CREATED.plusSeconds(30), null, null, CREATED, CREATED, CREATED);
    }
}
