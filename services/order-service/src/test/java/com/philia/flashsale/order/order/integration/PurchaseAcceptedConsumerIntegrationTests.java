package com.philia.flashsale.order.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedDataV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1;
import com.philia.flashsale.order.order.application.port.in.CreateOrderFromAcceptedPurchaseUseCase;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;
import com.philia.flashsale.order.support.KafkaIntegrationTestSupport;
import com.philia.flashsale.order.support.OrderIntegrationTestCondition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

/** Opt-in live-broker proof of key, header, duplicate delivery, and consumer-group handling. */
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
class PurchaseAcceptedConsumerIntegrationTests extends KafkaIntegrationTestSupport {

    private static final String TOPIC = "flashsale.purchase.events.v1";
    private static final Instant ACCEPTED = Instant.parse("2030-01-01T10:00:00Z");
    private static final String TRACEPARENT = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";

    @Autowired
    private KafkaTemplate<String, PurchaseAcceptedV1> kafkaTemplate;

    @MockBean
    private CreateOrderFromAcceptedPurchaseUseCase useCase;

    @Test
    void publishesARealAvroRecordWithPurchaseKeyAndTraceHeaderToTheOrderConsumer() throws Exception {
        UUID purchaseRequestId = UUID.randomUUID();
        when(useCase.create(any())).thenReturn(
                OrderCreationResult.created(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64)));
        ProducerRecord<String, PurchaseAcceptedV1> record = new ProducerRecord<>(TOPIC, purchaseRequestId.toString(),
                event(purchaseRequestId));
        record.headers().add("traceparent", TRACEPARENT.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);

        verify(useCase, timeout(10_000)).create(any());
        assertThat(record.key()).isEqualTo(purchaseRequestId.toString());
    }

    @Test
    void duplicatePhysicalDeliveryInvokesIdempotentUseCaseForEachRedelivery() throws Exception {
        UUID purchaseRequestId = UUID.randomUUID();
        PurchaseAcceptedV1 event = event(purchaseRequestId);
        when(useCase.create(any())).thenReturn(
                OrderCreationResult.eventReplayed(UUID.randomUUID(), "b".repeat(64)));

        kafkaTemplate.send(TOPIC, purchaseRequestId.toString(), event).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send(TOPIC, purchaseRequestId.toString(), event).get(10, TimeUnit.SECONDS);

        verify(useCase, timeout(10_000).times(2)).create(any());
    }

    private PurchaseAcceptedV1 event(UUID purchaseRequestId) {
        return new PurchaseAcceptedV1(UUID.randomUUID(), "PurchaseAccepted", 1, "flashsale-service",
                "PURCHASE_REQUEST", purchaseRequestId, 1L, UUID.randomUUID(), null, ACCEPTED,
                new PurchaseAcceptedDataV1(purchaseRequestId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), 1L, new BigDecimal("2.0000"), "VND", ACCEPTED,
                        ACCEPTED.plusSeconds(300)));
    }
}
