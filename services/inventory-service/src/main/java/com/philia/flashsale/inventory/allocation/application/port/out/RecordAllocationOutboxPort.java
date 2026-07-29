package com.philia.flashsale.inventory.allocation.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Persists the already-approved outbox intent; Kafka publication remains deferred. */
public interface RecordAllocationOutboxPort {
    void record(String aggregateType, UUID aggregateId, String eventType, String payload, Instant occurredAt);
}
