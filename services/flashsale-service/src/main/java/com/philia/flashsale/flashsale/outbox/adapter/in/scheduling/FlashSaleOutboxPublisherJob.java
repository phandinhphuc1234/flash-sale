package com.philia.flashsale.flashsale.outbox.adapter.in.scheduling;

import com.philia.flashsale.flashsale.outbox.application.usecase.OutboxPublicationService;
import java.time.Clock;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;

/** Polls durable outbox rows on the approved 500-millisecond cadence. */
public final class FlashSaleOutboxPublisherJob {
    private final OutboxPublicationService publication;
    private final Clock clock;
    private final String workerId;

    public FlashSaleOutboxPublisherJob(OutboxPublicationService publication) {
        this(publication, Clock.systemUTC(), defaultWorkerId());
    }

    FlashSaleOutboxPublisherJob(OutboxPublicationService publication, Clock clock, String workerId) {
        this.publication = Objects.requireNonNull(publication, "publication");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.workerId = Objects.requireNonNull(workerId, "workerId");
    }

    @Scheduled(fixedDelayString = "${flashsale.outbox.poll-interval:500ms}")
    public void publishDue() {
        publication.publishDue(workerId, clock.instant());
    }

    String workerId() {
        return workerId;
    }

    private static String defaultWorkerId() {
        String hostname = System.getenv("HOSTNAME");
        return hostname == null || hostname.isBlank() ? "flashsale-outbox-worker" : hostname;
    }
}
