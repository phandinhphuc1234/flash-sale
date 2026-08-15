package com.philia.flashsale.order.outbox.adapter.in.scheduling;

import com.philia.flashsale.order.outbox.application.usecase.OrderOutboxPublicationService;
import java.time.Clock;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;

/** Drives the leased outbox relay on the approved polling cadence. */
public final class OrderOutboxPublisherJob {
    private final OrderOutboxPublicationService publication;
    private final Clock clock;
    private final String workerId;

    public OrderOutboxPublisherJob(OrderOutboxPublicationService publication) {
        this(publication, Clock.systemUTC(), defaultWorkerId());
    }

    OrderOutboxPublisherJob(OrderOutboxPublicationService publication, Clock clock, String workerId) {
        this.publication = Objects.requireNonNull(publication, "publication");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.workerId = Objects.requireNonNull(workerId, "workerId");
    }

    @Scheduled(fixedDelayString = "${order.outbox.poll-interval:500ms}")
    public void publishDue() {
        publication.publishDue(workerId, clock.instant());
    }

    String workerId() {
        return workerId;
    }

    private static String defaultWorkerId() {
        String hostname = System.getenv("HOSTNAME");
        return hostname == null || hostname.isBlank() ? "order-outbox-worker" : hostname;
    }
}
