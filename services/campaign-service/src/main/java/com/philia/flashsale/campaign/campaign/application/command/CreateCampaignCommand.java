package com.philia.flashsale.campaign.campaign.application.command;

import java.time.Instant;

/** Input for creating a local editable Campaign draft. */
public record CreateCampaignCommand(
        String code,
        String name,
        Instant startAt,
        Instant endAt) {
}
