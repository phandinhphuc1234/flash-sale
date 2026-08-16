package com.philia.flashsale.order.outbox.adapter.in.scheduling;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.philia.flashsale.order.outbox.application.usecase.OrderOutboxPublicationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OrderOutboxPublisherJobTests {

    @Test
    void invokesPublicationWithStableWorkerAndUtcClock() {
        var publication = mock(OrderOutboxPublicationService.class);
        Instant now = Instant.parse("2030-01-01T10:00:00Z");
        var job = new OrderOutboxPublisherJob(publication, Clock.fixed(now, ZoneOffset.UTC), "worker-a");

        job.publishDue();

        verify(publication).publishDue("worker-a", now);
    }
}
