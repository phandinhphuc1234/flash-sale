package com.philia.flashsale.order.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedDataV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1;
import com.philia.flashsale.order.order.application.exception.RetryableOrderPersistenceException;
import com.philia.flashsale.order.order.application.port.in.CreateOrderFromAcceptedPurchaseUseCase;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;
import com.philia.flashsale.order.support.KafkaIntegrationTestSupport;
import com.philia.flashsale.order.support.OrderIntegrationTestCondition;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

/** Opt-in live proof of transient retries, non-retryable classification, and consumer DLT routing. */
@ExtendWith(OrderIntegrationTestCondition.class)
@SpringBootTest(properties = {
        "order.creation.enabled=false",
        "order.runtime.outbox-publisher-enabled=false",
        "order.kafka.retry-delays=1ms,2ms,3ms",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration"
})
class PurchaseAcceptedRetryDltIntegrationTests extends KafkaIntegrationTestSupport {

    private static final String TOPIC = "flashsale.purchase.events.v1";
    private static final String DLT = "flashsale.order.purchase-accepted.dlt.v1";
    private static final Instant ACCEPTED = Instant.parse("2030-01-01T10:00:00Z");

    @Autowired
    private KafkaTemplate<String, PurchaseAcceptedV1> kafkaTemplate;

    @Autowired
    private KafkaProperties kafkaProperties;

    @MockBean
    private CreateOrderFromAcceptedPurchaseUseCase useCase;

    @BeforeAll
    static void registerDltSchemaForControlledIntegrationFixture() throws Exception {
        String registryUrl = System.getProperty("SCHEMA_REGISTRY_URL", "http://localhost:8081");
        var registry = new CachedSchemaRegistryClient(registryUrl, 20);
        registry.register(DLT + "-" + PurchaseAcceptedV1.getClassSchema().getFullName(),
                PurchaseAcceptedV1.getClassSchema());
    }

    @Test
    void retriesTransientStorageFailureThenAcknowledgesAfterRecovery() throws Exception {
        UUID purchaseRequestId = UUID.randomUUID();
        RetryableOrderPersistenceException transientFailure =
                new RetryableOrderPersistenceException("temporary database failure", null);
        when(useCase.create(any())).thenThrow(transientFailure).thenReturn(
                OrderCreationResult.created(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64)));

        kafkaTemplate.send(TOPIC, purchaseRequestId.toString(), event(purchaseRequestId)).get(10, TimeUnit.SECONDS);

        verify(useCase, timeout(10_000).times(2)).create(any());
    }

    @Test
    void exhaustedTransientRetriesArePublishedToConsumerDlt() throws Exception {
        UUID purchaseRequestId = UUID.randomUUID();
        RetryableOrderPersistenceException transientFailure =
                new RetryableOrderPersistenceException("database remains unavailable", null);
        when(useCase.create(any())).thenThrow(transientFailure);

        kafkaTemplate.send(TOPIC, purchaseRequestId.toString(), event(purchaseRequestId)).get(10, TimeUnit.SECONDS);

        assertThat(pollDlt(purchaseRequestId.toString())).isTrue();
        verify(useCase, timeout(10_000).times(4)).create(any());
    }

    @Test
    void sendsMalformedEventToConsumerDltWithoutInvokingUseCase() throws Exception {
        UUID purchaseRequestId = UUID.randomUUID();
        PurchaseAcceptedV1 poison = new PurchaseAcceptedV1(UUID.randomUUID(), "PaymentRequested", 1,
                "flashsale-service", "PURCHASE_REQUEST", purchaseRequestId, 1L, UUID.randomUUID(), null,
                ACCEPTED, data(purchaseRequestId));

        kafkaTemplate.send(TOPIC, purchaseRequestId.toString(), poison).get(10, TimeUnit.SECONDS);

        assertThat(pollDlt(purchaseRequestId.toString())).isTrue();
        verify(useCase, never()).create(any());
    }

    private boolean pollDlt(String expectedKey) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "order-g4-dlt-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        properties.put("specific.avro.reader", true);
        properties.put("schema.registry.url", System.getProperty("SCHEMA_REGISTRY_URL", "http://localhost:8081"));
        var registry = new CachedSchemaRegistryClient(
                System.getProperty("SCHEMA_REGISTRY_URL", "http://localhost:8081"), 20);
        String registryUrl = System.getProperty("SCHEMA_REGISTRY_URL", "http://localhost:8081");
        var valueDeserializer = new KafkaAvroDeserializer(
                registry, Map.of("schema.registry.url", registryUrl, "specific.avro.reader", true));
        @SuppressWarnings({"rawtypes", "unchecked"})
        KafkaConsumer<String, PurchaseAcceptedV1> consumer = new KafkaConsumer(properties,
                new StringDeserializer(), valueDeserializer);
        try (consumer) {
            consumer.subscribe(java.util.List.of(DLT));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(java.time.Duration.ofMillis(250))) {
                    if (expectedKey.equals(record.key())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private PurchaseAcceptedV1 event(UUID purchaseRequestId) {
        return new PurchaseAcceptedV1(UUID.randomUUID(), "PurchaseAccepted", 1, "flashsale-service",
                "PURCHASE_REQUEST", purchaseRequestId, 1L, UUID.randomUUID(), null, ACCEPTED,
                data(purchaseRequestId));
    }

    private PurchaseAcceptedDataV1 data(UUID purchaseRequestId) {
        return new PurchaseAcceptedDataV1(purchaseRequestId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1L, new BigDecimal("2.0000"), "VND", ACCEPTED, ACCEPTED.plusSeconds(300));
    }
}
