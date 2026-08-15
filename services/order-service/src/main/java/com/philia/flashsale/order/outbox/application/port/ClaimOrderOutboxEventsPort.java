package com.philia.flashsale.order.outbox.application.port;

import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Claims due Order-created facts with a short, recoverable database lease. */
public interface ClaimOrderOutboxEventsPort {
    List<OrderOutboxEvent> claim(String workerId, Instant now, int batchSize, Duration lease);
}
