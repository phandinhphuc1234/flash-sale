package com.philia.flashsale.campaign.outbox.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Marks the claimed event as published after the broker acknowledges it. */
public interface MarkCampaignOutboxPublishedPort {

    boolean markPublished(UUID eventId, String workerId, Instant publishedAt);
}
