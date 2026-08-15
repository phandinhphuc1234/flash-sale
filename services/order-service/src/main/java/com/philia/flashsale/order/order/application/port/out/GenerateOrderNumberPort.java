package com.philia.flashsale.order.order.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Generates a bounded human-readable Order number. */
public interface GenerateOrderNumberPort {
    String generate(Instant createdAt, UUID orderId);
}
