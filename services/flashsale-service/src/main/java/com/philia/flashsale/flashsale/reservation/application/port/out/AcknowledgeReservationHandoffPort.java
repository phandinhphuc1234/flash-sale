package com.philia.flashsale.flashsale.reservation.application.port.out;

import java.util.UUID;

/** Acknowledges a Redis Stream entry only after its durable outcome is committed. */
public interface AcknowledgeReservationHandoffPort {
    void acknowledge(UUID reservationId, String handoffEntryId);
}
