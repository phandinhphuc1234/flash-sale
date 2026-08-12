package com.philia.flashsale.flashsale.outbox.application.port;

import com.philia.flashsale.flashsale.outbox.application.model.OutboxEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Claims due publication intents using a short database lease. */
public interface ClaimOutboxEventsPort {
    List<OutboxEvent> claim(String workerId, Instant now, int batchSize, Duration lease);
}
