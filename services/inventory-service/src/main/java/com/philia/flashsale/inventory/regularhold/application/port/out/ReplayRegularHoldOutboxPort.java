package com.philia.flashsale.inventory.regularhold.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Re-enqueues one existing fact with its original identity after a duplicate command delivery. */
public interface ReplayRegularHoldOutboxPort {
    void requeue(UUID eventId, Instant now);
}
