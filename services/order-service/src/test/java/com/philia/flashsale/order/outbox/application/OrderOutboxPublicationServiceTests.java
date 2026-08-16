package com.philia.flashsale.order.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.outbox.application.port.ClaimOrderOutboxEventsPort;
import com.philia.flashsale.order.outbox.application.port.PublishOrderCreatedPort;
import com.philia.flashsale.order.outbox.application.port.UpdateOrderOutboxPublicationPort;
import com.philia.flashsale.order.outbox.application.usecase.OrderOutboxPublicationService;
import com.philia.flashsale.order.outbox.application.usecase.OrderOutboxRetryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderOutboxPublicationServiceTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void publishesClaimedFactThenMarksItPublishedWithWorkerLease() {
        var event = event(1, "IN_PROGRESS");
        var claims = new FakeClaims(event);
        var updates = new FakeUpdates();
        var publisher = new FakePublisher();
        var service = service(claims, publisher, updates);

        assertThat(service.publishDue("worker-a", NOW)).isEqualTo(1);
        assertThat(claims.workerId).isEqualTo("worker-a");
        assertThat(publisher.published).containsExactly(event);
        assertThat(updates.publishedEventId).isEqualTo(event.eventId());
        assertThat(updates.publishedWorker).isEqualTo("worker-a");
    }

    @Test
    void publicationFailureIsSanitizedAndRequeuedWithExponentialDelay() {
        var event = event(2, "IN_PROGRESS");
        var claims = new FakeClaims(event);
        var updates = new FakeUpdates();
        var publisher = new FakePublisher();
        publisher.failure = new IllegalStateException("secret broker details");
        var service = service(claims, publisher, updates);

        service.publishDue("worker-b", NOW);

        assertThat(updates.failedEventId).isEqualTo(event.eventId());
        assertThat(updates.failedWorker).isEqualTo("worker-b");
        assertThat(updates.nextAttemptAt).isEqualTo(NOW.plusSeconds(2));
        assertThat(updates.error).isEqualTo("outbox publication failed: IllegalStateException");
        assertThat(updates.error).doesNotContain("secret broker details");
    }

    @Test
    void retriesTheSameIdentityAfterARecoveredDependency() {
        var event = event(1, "IN_PROGRESS");
        var claims = new FakeClaims(event);
        var updates = new FakeUpdates();
        var publisher = new FakePublisher();
        publisher.failure = new IllegalStateException("broker unavailable");
        var service = service(claims, publisher, updates);

        service.publishDue("worker-a", NOW);
        publisher.failure = null;
        service.publishDue("worker-a", NOW.plusSeconds(2));

        assertThat(publisher.published).containsExactly(event);
        assertThat(updates.failedEventId).isEqualTo(event.eventId());
        assertThat(updates.publishedEventId).isEqualTo(event.eventId());
    }

    private static OrderOutboxPublicationService service(FakeClaims claims, FakePublisher publisher,
            FakeUpdates updates) {
        return new OrderOutboxPublicationService(claims, publisher, updates,
                new OrderOutboxRetryPolicy(Duration.ofSeconds(60)), 100, Duration.ofSeconds(30));
    }

    private static OrderOutboxEvent event(int attempt, String status) {
        UUID id = UUID.randomUUID();
        Instant created = NOW.minusSeconds(10);
        return new OrderOutboxEvent(id, "ORDER", id, 1, "OrderCreated", 1, id.toString(), UUID.randomUUID(),
                UUID.randomUUID(), "{\"orderId\":\"" + id + "\"}", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01",
                null, status, attempt, NOW, "worker", NOW.plusSeconds(30), null, null, NOW, created, created);
    }

    private static final class FakeClaims implements ClaimOrderOutboxEventsPort {
        private final OrderOutboxEvent event;
        private String workerId;

        private FakeClaims(OrderOutboxEvent event) {
            this.event = event;
        }

        @Override
        public List<OrderOutboxEvent> claim(String workerId, Instant now, int batchSize, Duration lease) {
            this.workerId = workerId;
            return List.of(event);
        }
    }

    private static final class FakePublisher implements PublishOrderCreatedPort {
        private final java.util.ArrayList<OrderOutboxEvent> published = new java.util.ArrayList<>();
        private RuntimeException failure;

        @Override
        public void publish(OrderOutboxEvent event) {
            if (failure != null) {
                throw failure;
            }
            published.add(event);
        }
    }

    private static final class FakeUpdates implements UpdateOrderOutboxPublicationPort {
        private UUID publishedEventId;
        private String publishedWorker;
        private UUID failedEventId;
        private String failedWorker;
        private Instant nextAttemptAt;
        private String error;

        @Override
        public void markPublished(UUID eventId, String workerId, Instant publishedAt) {
            publishedEventId = eventId;
            publishedWorker = workerId;
        }

        @Override
        public void markFailed(UUID eventId, String workerId, Instant failedAt, Instant nextAttemptAt,
                String sanitizedError) {
            failedEventId = eventId;
            failedWorker = workerId;
            this.nextAttemptAt = nextAttemptAt;
            error = sanitizedError;
        }
    }
}
