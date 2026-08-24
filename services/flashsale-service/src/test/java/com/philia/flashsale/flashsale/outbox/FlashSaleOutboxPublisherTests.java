package com.philia.flashsale.flashsale.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1;
import com.philia.flashsale.flashsale.configuration.OutboxProperties;
import com.philia.flashsale.flashsale.outbox.adapter.out.messaging.kafka.KafkaPurchaseAcceptedPublisher;
import com.philia.flashsale.flashsale.outbox.adapter.out.messaging.kafka.PurchaseAcceptedAvroMapper;
import com.philia.flashsale.flashsale.outbox.application.model.OutboxEvent;
import com.philia.flashsale.flashsale.outbox.application.port.ClaimOutboxEventsPort;
import com.philia.flashsale.flashsale.outbox.application.port.PublishPurchaseAcceptedPort;
import com.philia.flashsale.flashsale.outbox.application.port.PublishOutboxEventPort;
import com.philia.flashsale.flashsale.outbox.application.port.UpdateOutboxPublicationPort;
import com.philia.flashsale.flashsale.outbox.application.usecase.FlashSaleOutboxEventTypeDispatcher;
import com.philia.flashsale.flashsale.outbox.application.usecase.OutboxPublicationService;
import com.philia.flashsale.flashsale.outbox.application.usecase.OutboxRetryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class FlashSaleOutboxPublisherTests {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

    @Test
    void retriesSameEventWithCappedExponentialBackoffAndSanitizedError() {
        OutboxEvent event = event(1);
        ClaimOutboxEventsPort claims = (worker, now, batch, lease) -> List.of(event);
        PublishPurchaseAcceptedPort publisher = ignored -> {
            throw new IllegalStateException("secret payload must not be stored");
        };
        UpdateOutboxPublicationPort updates = mock(UpdateOutboxPublicationPort.class);
        OutboxPublicationService service = new OutboxPublicationService(claims, publisher, updates,
                new OutboxRetryPolicy(Duration.ofSeconds(60)), 100, Duration.ofSeconds(30));

        assertThat(service.publishDue("worker-a", NOW)).isEqualTo(1);

        verify(updates).markFailed(event.eventId(), NOW, NOW.plusSeconds(1),
                "outbox publication failed: IllegalStateException");
    }

    @Test
    void publishesStableKeyEventPayloadAndTraceHeaders() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, PurchaseAcceptedV1> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        KafkaPurchaseAcceptedPublisher publisher = new KafkaPurchaseAcceptedPublisher(kafka,
                new PurchaseAcceptedAvroMapper(), new OutboxProperties("flashsale.purchase.events.v1",
                        Duration.ofMillis(500), 100, Duration.ofSeconds(30), Duration.ofSeconds(60)));

        OutboxEvent event = event(0);
        publisher.publish(event);

        var captured = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captured.capture());
        ProducerRecord<?, ?> record = captured.getValue();
        assertThat(record.topic()).isEqualTo("flashsale.purchase.events.v1");
        assertThat(record.key()).isEqualTo(event.aggregateId().toString());
        assertThat(((PurchaseAcceptedV1) record.value()).getEventId()).isEqualTo(event.eventId());
        assertThat(((PurchaseAcceptedV1) record.value()).getData().getUnitPrice().toPlainString()).isEqualTo("19.9900");
        assertThat(record.headers().lastHeader("traceparent").value())
                .isEqualTo("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01".getBytes());
    }

    @Test
    void retryPolicyDoublesUntilTheSixtySecondCap() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(Duration.ofSeconds(60));

        assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.delayForAttempt(7)).isEqualTo(Duration.ofSeconds(60));
        assertThat(policy.delayForAttempt(20)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void dispatchesAcceptedEventThroughGenericOutboxPort() {
        var received = new java.util.ArrayList<UUID>();
        PublishOutboxEventPort publisher = event -> received.add(event.eventId());
        var dispatcher = new FlashSaleOutboxEventTypeDispatcher(Map.of("PurchaseAccepted", publisher));
        var event = event(0);

        dispatcher.publish(event);

        assertThat(received).containsExactly(event.eventId());
    }

    @Test
    void keepsResultPublishersDisabledUntilTheirStoryRegistersOne() {
        var dispatcher = new FlashSaleOutboxEventTypeDispatcher(Map.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dispatcher.publish(
                new OutboxEvent(UUID.randomUUID(), "RESERVATION", UUID.randomUUID(), 1,
                        "PurchaseReservationConfirmed", 1, Map.of(), "PENDING", 0,
                        NOW, null, null, null, null, NOW, NOW)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PurchaseReservationConfirmed");
    }

    private static OutboxEvent event(int attemptCount) {
        UUID purchaseRequestId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purchaseRequestId", purchaseRequestId.toString());
        payload.put("reservationId", UUID.randomUUID().toString());
        payload.put("campaignId", UUID.randomUUID().toString());
        payload.put("variantId", UUID.randomUUID().toString());
        payload.put("userId", UUID.randomUUID().toString());
        payload.put("quantity", 2L);
        payload.put("unitPrice", "19.9900");
        payload.put("currency", "VND");
        payload.put("acceptedAt", NOW.toString());
        payload.put("expiresAt", NOW.plusSeconds(300).toString());
        payload.put("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
        payload.put("tracestate", "vendor=value");
        return new OutboxEvent(UUID.randomUUID(), "PURCHASE_REQUEST", purchaseRequestId, 1,
                "PurchaseAccepted", 1, payload, "PROCESSING", attemptCount, NOW, "worker-a",
                NOW.plusSeconds(30), null, null, NOW, NOW);
    }
}
