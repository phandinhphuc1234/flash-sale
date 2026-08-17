package com.philia.flashsale.order.order.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedDataV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1;
import com.philia.flashsale.order.order.adapter.in.messaging.kafka.PurchaseAcceptedAvroMapper;
import com.philia.flashsale.order.order.adapter.in.messaging.kafka.PurchaseAcceptedKafkaConsumer;
import com.philia.flashsale.order.order.application.port.in.CreateOrderFromAcceptedPurchaseUseCase;
import com.philia.flashsale.order.support.PostgreSqlIntegrationTestSupport;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.Acknowledgment;

/**
 * Opt-in nominal measurement of delivered PurchaseAccepted-to-commit latency.
 *
 * <p>The test deliberately calls the inbound adapter with a delivered Kafka record while using a
 * real PostgreSQL container. It measures the durable commit boundary, not Kafka network latency,
 * and reconciles the four service-owned rows afterwards. Enable with
 * {@code -Dorder.event-to-commit.enabled=true}.</p>
 */
@EnabledIfSystemProperty(named = "order.event-to-commit.enabled", matches = "true")
@SpringBootTest(properties = {
        "order.runtime.accepted-purchase-consumer-enabled=false",
        "order.runtime.outbox-publisher-enabled=false",
        "order.outbox.enabled=false",
        "order.query.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=localhost:19092"
})
class OrderConsumerPerformanceIntegrationTests extends PostgreSqlIntegrationTestSupport {

    private static final String TOPIC = "flashsale.purchase.events.v1";
    private static final Instant ACCEPTED_AT = Instant.parse("2030-01-01T10:00:00Z");

    @Autowired
    private PurchaseAcceptedAvroMapper mapper;

    @Autowired
    private CreateOrderFromAcceptedPurchaseUseCase useCase;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deliveredEventToCommitProfilePersistsOneReconciledOrder() {
        UUID purchaseRequestId = UUID.randomUUID();
        PurchaseAcceptedV1 event = event(purchaseRequestId);
        ConsumerRecord<String, PurchaseAcceptedV1> record = new ConsumerRecord<>(
                TOPIC, 0, System.nanoTime(), purchaseRequestId.toString(), event);
        AtomicBoolean acknowledged = new AtomicBoolean();
        Acknowledgment acknowledgment = () -> acknowledged.set(true);
        PurchaseAcceptedKafkaConsumer consumer = new PurchaseAcceptedKafkaConsumer(mapper, useCase);

        long started = System.nanoTime();
        consumer.onMessage(record, acknowledgment);
        long commitMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        UUID orderId = jdbc.queryForObject(
                "select id from orders where purchase_request_id = ?", UUID.class, purchaseRequestId);
        assertThat(acknowledged).as("Kafka acknowledgement follows the committed use case").isTrue();
        assertThat(orderId).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from order_lines where order_id = ?", Integer.class, orderId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from order_consumer_inbox where purchase_request_id = ?",
                Integer.class, purchaseRequestId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from order_outbox_events where aggregate_id = ?",
                Integer.class, orderId)).isEqualTo(1);
        System.out.printf("ORDER_EVENT_TO_COMMIT_MS=%d orderId=%s purchaseRequestId=%s%n",
                commitMillis, orderId, purchaseRequestId);
    }

    private PurchaseAcceptedV1 event(UUID purchaseRequestId) {
        return new PurchaseAcceptedV1(UUID.randomUUID(), "PurchaseAccepted", 1, "flashsale-service",
                "PURCHASE_REQUEST", purchaseRequestId, 1L, UUID.randomUUID(), UUID.randomUUID(), ACCEPTED_AT,
                new PurchaseAcceptedDataV1(purchaseRequestId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), 1L, new BigDecimal("90000.0000"), "VND", ACCEPTED_AT,
                        ACCEPTED_AT.plusSeconds(300)));
    }
}
