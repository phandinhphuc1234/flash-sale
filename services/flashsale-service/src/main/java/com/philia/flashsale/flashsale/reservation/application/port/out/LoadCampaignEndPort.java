package com.philia.flashsale.flashsale.reservation.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Reads only the projected Campaign end boundary needed to retain idempotency safely. */
public interface LoadCampaignEndPort {
    Optional<Instant> loadEndsAt(UUID campaignId);
}
