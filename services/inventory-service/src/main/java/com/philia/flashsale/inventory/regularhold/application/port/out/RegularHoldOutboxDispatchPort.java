package com.philia.flashsale.inventory.regularhold.application.port.out;

import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldOutboxEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Claims and settles only durable facts emitted by the regular stock-hold aggregate. */
public interface RegularHoldOutboxDispatchPort {
    List<RegularHoldOutboxEvent> claimDue(String workerId, Instant now, Duration lease, int batchSize);

    boolean markPublished(UUID eventId, String workerId, Instant now);

    boolean recordFailure(UUID eventId, String workerId, Instant now, List<Duration> retryDelays);
}
