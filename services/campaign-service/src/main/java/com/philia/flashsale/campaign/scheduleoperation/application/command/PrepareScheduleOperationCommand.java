package com.philia.flashsale.campaign.scheduleoperation.application.command;

import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperationFingerprint;
import java.util.Objects;
import java.util.UUID;

/** Input required to create or resume one retained Campaign scheduling operation. */
public record PrepareScheduleOperationCommand(
        UUID campaignId,
        String idempotencyKey,
        ScheduleOperationFingerprint fingerprint,
        long campaignVersion,
        String initiatedBy,
        String callerService,
        String traceId) {

    public PrepareScheduleOperationCommand {
        Objects.requireNonNull(campaignId, "Campaign id is required");
        Objects.requireNonNull(fingerprint, "Schedule operation fingerprint is required");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Schedule operation idempotency key is required");
        }
        if (campaignVersion < 0) {
            throw new IllegalArgumentException("Campaign version must not be negative");
        }
        if (initiatedBy == null || initiatedBy.isBlank()) {
            throw new IllegalArgumentException("Schedule operation initiator is required");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("Schedule operation trace id is required");
        }
    }
}
