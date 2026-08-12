package com.philia.flashsale.campaign.campaign.adapter.in.web.admin.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** HTTP input for replacing all editable metadata of a draft Campaign. */
public record ReplaceCampaignMetadataRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 200, message = "name must not exceed 200 characters")
        String name,
        @NotNull(message = "startAt must not be null")
        Instant startAt,
        @NotNull(message = "endAt must not be null")
        Instant endAt) {
}
