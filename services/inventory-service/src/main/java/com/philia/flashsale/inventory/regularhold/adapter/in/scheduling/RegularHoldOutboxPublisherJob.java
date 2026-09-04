package com.philia.flashsale.inventory.regularhold.adapter.in.scheduling;

import com.philia.flashsale.inventory.configuration.InventoryRegularHoldProperties;
import com.philia.flashsale.inventory.regularhold.adapter.out.messaging.kafka.RegularHoldOutcomePublisher;
import com.philia.flashsale.inventory.regularhold.application.port.out.RegularHoldOutboxDispatchPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Relays leased facts without holding a database transaction during Kafka I/O. */
@Component
@ConditionalOnProperty(name = "flashsale.inventory.regular-hold.outbox-publisher-enabled", havingValue = "true")
public class RegularHoldOutboxPublisherJob {
    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);
    private static final int BATCH_SIZE = 100;
    private final RegularHoldOutboxDispatchPort dispatcher;
    private final RegularHoldOutcomePublisher publisher;
    private final InventoryRegularHoldProperties properties;
    private final Clock clock;
    private final String workerId = "inventory-regular-hold-outbox-" + UUID.randomUUID();

    public RegularHoldOutboxPublisherJob(RegularHoldOutboxDispatchPort dispatcher,
            RegularHoldOutcomePublisher publisher, InventoryRegularHoldProperties properties, Clock inventoryClock) {
        this.dispatcher = dispatcher;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = inventoryClock;
    }

    @Scheduled(fixedDelayString = "${flashsale.inventory.regular-hold.outbox-scan-delay:500ms}")
    public void publishDue() {
        Instant now = clock.instant();
        dispatcher.claimDue(workerId, now, CLAIM_LEASE, BATCH_SIZE).forEach(event -> {
            try {
                publisher.publish(event);
                dispatcher.markPublished(event.eventId(), workerId, clock.instant());
            } catch (RuntimeException exception) {
                dispatcher.recordFailure(event.eventId(), workerId, clock.instant(), properties.getRetryDelays());
            }
        });
    }
}
